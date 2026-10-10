# Android Lint 门禁与提速

> **适用范围**：本项目 `./gradlew :app:lintAppMaxDebug`（Android Lint）作为本地/CI 门禁的机制、一次真实排障实录、范围自适应改造，以及"为什么慢 / 怎么才真能快"的结论。
> **时效**：长期有效的工程/构建技术笔记。lint 配置以 `app/build.gradle` 的 `lint {}` + `lintResolveScope()` 为准，CI 以 `.github/workflows/lint.yaml` 为准，**发现与代码不符一律以代码为准**。
> 相关：命令矩阵见 [docs/project-rules/build-commands.md](../project-rules/build-commands.md) 的「Android Lint 门禁与提速」。

## 1. Lint 在门禁链里的定位

- 验证顺序：**单测 → lint**，lint 是本地最后一道门禁；CI 中"lint 通过"算作"完成"的一部分。
- CI 入口是**单变体** `:app:lintAppMaxDebug`（不是全 `lint` 矩阵，避免全 flavor×buildType 拖慢 CI）。
- `abortOnError` 生效：只要出现 **`lint-baseline.xml` 之外的新增 error**，任务直接 `Execution failed for task ':app:lintAppMaxDebug'` → `BUILD FAILED`，报错首行是 `Lint found N errors …; aborting build.`。
- 存量问题基线：`app/lint-baseline.xml` 一次性收录历史问题，此后只拦新增。存量修完后基线要刷新（`-DupdateBaseline=true` 或 IDE），不要手改基线文件。

一次典型输出怎么读：

```
Lint found 2 errors, 63 warnings and 1 hint. First failure:
… (and 55 errors, 1542 warnings, 38 hints filtered by baseline lint-baseline.xml)
```

- 前半段（`found 2 errors`）= **baseline 之外的新增**，才是导致失败的元凶，必须处理；
- 括号里（`filtered by baseline`）= 被基线吞掉的存量，不影响门禁。

## 2. 一次排障实录：2 个新增 Compose error

现象："Android lint 没跑通"。跑 `:app:lintAppMaxDebug` 冷跑约 7 分钟后 `BUILD FAILED`，报告里 2 个 baseline 外的新增 error：

| 位置                            | 检查 ID（来源）                                      | 问题                                                                                  | 修复                                                        |
| ------------------------------- | ---------------------------------------------------- | ------------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| `SourceInfoDialog.kt:158`       | `NonObservableLocale`（androidx.compose.ui）         | composable 里用 `Locale.getDefault()` 构造 `SimpleDateFormat`，非可观察，切语言不刷新 | 从 `LocalLocale.current` 取语言/地区构造 `java.util.Locale` |
| `SourceRecycleBinScreen.kt:108` | `ContextCastToActivity`（androidx.activity.compose） | `LocalContext.current as? AppCompatActivity` 强转 Activity                            | 改用 `LocalActivity.current as? AppCompatActivity`          |

### 坑 1：`LocalLocale.current` 不再是 `java.util.Locale`

Compose BOM 2026.08 起，`androidx.compose.ui.platform.LocalLocale.current` 返回新类型 `androidx.compose.ui.text.intl.Locale`，**直接传给 `SimpleDateFormat(String, java.util.Locale)` 会编译失败**（`None of the following candidates is applicable`）。lint 的修复提示虽写 `LocalLocale.current`，但要落到 `java.util.Locale`：

```kotlin
val composeLocale = LocalLocale.current
SimpleDateFormat(
    "yyyy-MM-dd HH:mm:ss",
    Locale(composeLocale.language, composeLocale.region)   // language + region 足够日期格式化
).format(Date(lastUpdateTime))
```

> 没有直接用 `LocalConfiguration.current.locales[0]`：`Configuration.getLocales()` 是 API 24，而本项目 minSdk 23，存在运行期风险；用 compose `Locale` 的 `language`/`region` 构造 `java.util.Locale` 跨版本安全且仍可观察。

### 坑 2：日志里的 `JoinEffectDetector` NPE 是"假警报"

分析阶段日志出现：

```
Error while indexing class null
java.lang.NullPointerException: Cannot invoke "com.intellij.psi.PsiFile.getVirtualFile()" because "file" is null
    at com.android.tools.lint.checks.fx.JoinEffectDetector.visitClass(...)
```

这是 **lint 工具自带 `fx` 检查的内部非致命报错**，lint 记录后照常完成分析，**不是** BUILD FAILED 的原因。真正的失败永远是那句 `Lint found N errors …; aborting build.`。排查时不要被这段堆栈带偏。

## 3. 范围自适应改造（`lintResolveScope()`）

动机：默认 `checkDependencies = true` 让 app 的 lint 连带分析 `:modules:book`、`:modules:rhino` 等依赖模块，且 androidTest / unitTest 源码各起一轮 analyze。希望"只改主源码时缩小范围"。

落地：`app/build.gradle` 顶层加 `lintResolveScope()` 闭包，在 `lint {}` 块里按返回值设置 `checkDependencies` / `checkTestSources`。决策矩阵：

| 条件                               | 结果                                           |
| ---------------------------------- | ---------------------------------------------- |
| 显式 `-PlintFull`                  | 全量                                           |
| 显式 `-PlintFast`                  | 快跑                                           |
| env `CI` 或 `GITHUB_ACTIONS` 存在  | 全量（**门禁永不降级**）                       |
| 本次调用不含 lint/check/build 任务 | 直接返回，不跑 git、不打日志（普通构建零负担） |
| 其余（本地且确要跑 lint）          | 用 git 判定改动路径（见下）                    |

git 判定：`git diff --name-only <base>...HEAD` + `git status --porcelain -u` 汇总改动文件；命中任一即**全量**，否则**快跑**：

- `modules/**`（依赖库模块）
- `**/src/test/**`、`**/src/androidTest/**`（测试源码）
- 任意 `build.gradle`、`settings.gradle`、`gradle.properties`、`gradle/libs.versions.toml`、`lint-baseline.xml`、`app/lint.xml`（构建/lint 配置）

稳健性：

- 用 `providers.exec { }.result.get().exitValue` 判断 git 成败，**非零退出（如基线 ref 不存在）→ 保守回退全量**（不能用 `ignoreExitValue` + 合并 stdout/stderr，否则 `fatal:` 文本会被误当成改动文件，错误降级为快跑）。
- 基线默认 `origin/main`，可用 `-PlintBase=<ref>` 覆盖；首次本地使用前需 `git fetch origin main`（同 spotless `ratchetFrom` 前提）。
- 决策时打一行 `[lint] 范围=…（原因：…）`，只在真正跑 lint 时输出。

## 4. 提速真相：范围裁剪几乎没用

实测（同一台机器、冷跑）：

| 模式 | 命令                                                  | 墙钟    |
| ---- | ----------------------------------------------------- | ------- |
| 全量 | `:app:lintAppMaxDebug`（原 `checkDependencies=true`） | ≈ 7m    |
| 快跑 | `:app:lintAppMaxDebug -PlintFast`                     | ≈ 7m28s |

范围确实缩小了（baseline 过滤掉的 warning 从 1542 → 1534），但**墙钟几乎不变**。原因：**本项目 lint 耗时大头是 app 主源码自身的 UAST 分析**（`:app:lintAnalyzeAppMaxDebug`），两种模式都要完整跑；而 `:modules:book` / `:modules:rhino` 体量小、测试源码更少，关掉省不了多少。

结论：自适应改动**留着无害**（范围语义清晰、CI 不降级、普通构建零开销），但**别指望它把 7 分钟砍下来**。

## 5. 真正有效的提速办法

1. **迭代期用 IDE 内联 lint，CLI 只作收尾门禁**——Android Studio 的实时 lint 是按文件增量、改哪查哪；CLI 全量 `:app:lintAppMaxDebug` 只在 push 前 / CI 跑。这是最大的一条。
2. **保住增量缓存**——lint 本身跨运行增量（`build/intermediates/lint-cache`）。反复 `gradlew --stop` / `clean` 会把它打回冷跑。
3. **别在改代码循环里反复跑 CLI lint**——写完一批再跑一次。
4. Gradle 层性能项本仓库已拉满（`parallel` / `caching` / `vfs.watch` / `-Xmx6g`），configuration-cache 对 lint 兼容性一般，不为 lint 单独开。

## 6. Windows / pwsh 跑 CLI lint 的操作坑

排查这次踩到的，供后续调试参考：

- **后台 + Gradle daemon 的输出会被缓冲**：`is_background` 直接启动的 pwsh→sandbox→`gradlew | Tee` 组合经常不刷新、甚至不生成日志文件；而**前台跑（超时会自动转后台终端）**用 `Tee-Object -FilePath xxx.log` 能边跑边写、可靠读进度。
- **`cd /d` 是 cmd 语法**，pwsh 下无效；工作目录已是仓库根，直接 `.\gradlew.bat …` 即可。
- **禁止在 PowerShell 环境用 `cmd /c`**（工具会拦截），改用纯 pwsh 管道。
- **紧接上一次 gradle 调用后立刻再跑 `gradlew.bat` 偶发"拒绝访问"**（文件锁未释放）：先 `gradlew --stop` + 稍等 + 确认 java 进程为 0 再重跑。
