# CI 打包并发控制与 R8 混淆技术笔记

> 整理时间：2026-08-27

---

## 一、GitHub Actions Matrix 并发控制

### 1.1 背景

`test.yml` 的 `build` 任务使用 matrix strategy，一次构建 4 个变体：

| 变体 | Gradle 任务 | 说明 |
|------|------------|------|
| release | `assembleAppMaxRelease` | 测试版 |
| releaseS | `assembleAppSRelease` | 共存版 |
| releaseLegacy | `assembleAppLegacyRelease` | 兼容版 |
| debug | `assembleAppMaxDebug` | Debug 调试包 |

`release.yml` 的 `build` 任务使用 matrix，一次构建 3 个产品：

| 产品 | 说明 |
|------|------|
| appS | 共存版 |
| appMax | 测试版 |
| appLegacy | 兼容版 |

### 1.2 问题：默认全部并行

GitHub Actions 的 matrix strategy 默认不设 `max-parallel`，所有组合全部同时跑。之前两个 workflow 都没有这个限制。

### 1.3 解决方案：添加 `max-parallel: 2`

在 `strategy` 下添加 `max-parallel: 2`，限制同一时间最多 2 个构建并行。

**修改位置：**

- `.github/workflows/test.yml`：`build` 任务的 `strategy` 块
- `.github/workflows/release.yml`：`build` 任务的 `strategy` 块

```yaml
strategy:
  matrix: ...
  fail-fast: false
  max-parallel: 2   # ← 新增
```

### 1.4 为什么是 2 而不是 4

**原因：内存瓶颈，不是 CPU。**

GitHub Actions `ubuntu-latest` runner 规格为 **2 vCPU / 7GB RAM**。

Release 包开启 R8 混淆（`minifyEnabled = true`），R8 的 program 精简 + 引用重定向步骤**单任务吃 2-4GB 内存**。如果 4 个 Release 变体同时跑 R8：

- 4 × 3GB ≈ 12GB → Runner 只有 7GB → **OOM Kill 或频繁 GC**
- 设 `max-parallel: 2` 后，并发内存约 6GB，在安全线内

Debug 包不开混淆，内存压力小，排在后面不影响整体时间。

### 1.5 Runner 概念

Workflow 定义的是"要做什么"，真正执行任务的机器叫 Runner。`runs-on: ubuntu-latest` 指定使用 GitHub 提供的 Ubuntu 虚拟机。类比：Workflow = 菜单，Runner = 厨房灶台。

---

## 二、R8 混淆与代码压缩

### 2.1 R8 是什么

R8 是 Android 官方的代码压缩和优化工具，取代了旧版 ProGuard。Release 包通过 `minifyEnabled = true` 开启。

**项目配置**（`app/build.gradle`）：

```gradle
release {
    minifyEnabled = true
    shrinkResources = true
    proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'),
                'proguard-rules.pro',
                'cronet-proguard-rules.pro'
}
```

Debug 包默认不开启（`minifyEnabled = false`）。

### 2.2 R8 的能力

| 能力 | 说明 |
|------|------|
| 代码压缩 | 剔除从未调用的类/方法/字段 |
| 代码优化 | 字节码重写，减少分配和虚方法调用 |
| 引用重定向 | 短链化包名，减小字符串常量池 |
| 资源压缩 | 剔除从未引用的资源（图标、字符串等） |
| 字节码重写 | interface 调用 → 直接调用 |

Release 包经 R8 后，APK 体积通常减少 30%~50%。

### 2.3 R8 对运行速度的影响

- ✅ **启动变快**：无用类被剔除，ART 虚拟机少加载类
- ✅ **内存变小**：字符串常量池精简
- ✅ **方法调用优化**：虚方法 → 直接调用
- ❌ **业务逻辑不加速**：不改变算法复杂度

### 2.4 "少加载几千个类"的含义

以第三方库为例：引用了某个库（如 Retrofit），库里有 200 个类，代码只用了 5 个。

- **Debug 包**：APK 包含全部 200 个类，启动时全部加载、验证、初始化
- **Release 包**：R8 只保留 5 个被引用的类，其余剔除

Debug 包启动慢 100-200ms 在开发中无所谓，但用户感知到的 Release 包启动快慢就是实打实的体验差异。

### 2.5 "无用类"的定义

R8 做的是**全程序可达性分析**（Reachability Analysis），从固定入口点出发，递归找所有能被触达的代码：

```
入口点（始终保留）
  ├── Application 类
  ├── Activity / Service /BroadcastReceiver / ContentProvider
  ├── JNI native 方法
  ├── 反射入口（注解、manifest 声明的类）
  └── ... 递归找所有 new / 调用 / 字段引用的类
```

**在入口点链条上走不到的类 = 被砍。**

### 2.6 重要陷阱：反射

```kotlin
Class.forName("com.example.UnusedHelper")  // R8 静态分析看不到这行
```

- 默认情况下，`UnusedHelper` 会被砍掉 → **运行时 Crash**
- 需要在 `proguard-rules.pro` 中手动 keep：
  ```
  -keep class com.example.UnusedHelper { *; }
  ```

### 2.7 项目中的 keep 规则

`app/build.gradle` 引用了两个自定义规则文件：

- `proguard-rules.pro`：项目自定义的 keep 规则
- `cronet-proguard-rules.pro`：Cronet 网络库的 keep 规则（防止 R8 误砍 Cronet 内部通过反射或 JNI 调用的类）

### 2.8 资源压缩与图标包

R8 的资源压缩扫描代码和 XML 布局，只保留被引用的资源。如果 App 内有多个主题图标包，只有代码中 `android:icon` 引用到的那一个保留，其余自动从 APK 中排除。

---

## 三、Related Changes

- commit `d11deb313`：ci(构建): matrix strategy 添加 max-parallel: 2，限制同时并发打包数
- commit `47b8626db`：fix(构建): ABI分拆仅对Release任务生效，Debug恢复单包产出