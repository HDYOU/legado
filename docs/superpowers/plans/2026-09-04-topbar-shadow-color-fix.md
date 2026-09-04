# 顶栏修复实现计划（阴影消失 + 颜色不一致）

> **面向 AI 代理的工作者：** 必需子技能：使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 修复顶栏阴影消失（regular 风格圆角下阴影被强制置零）并统一所有仍用旧 `pageTopBarContainerColor()` / 硬编码 `colorScheme.secondary` 页面的顶栏颜色到新 `TopBarConfig` 标准。

**架构：** 顶层 API 已在 `CommonPageColors.kt` 定义：`pageTopBarColors()` 返回 `PageTopBarColors`（含 containerColor/contentColor/cornerRadius/shadowElevation/wallpaper），`Modifier.pageTopBarBackground(colors)` 承载 fillMaxWidth + shadow + clip + background + wallpaper。`AppPageTopBar` 已接此新 API。修复分两块：① 调整 `shadowElevation` 计算分支让 regular 风格圆角也有阴影；② 把所有旧 API 调用点迁移到 `pageTopBarColors()` + `pageTopBarBackground` + `contentColor` 统一模式。

**技术栈：** Compose Material3 `TopAppBar` / `AppScaffold` / `TopBarConfig`，Kotlin，Gradle wrapper（JDK 17）。

---

## 背景 / 根因

### 问题 1：阴影消失

提交 `e7f6e9832b26bf03768233021a0643a164c22f6f` 把 `AppPageTopBar` 从「纯色 secondary 容器」改为通过 `pageTopBarBackground` 渲染（含阴影）。但 `CommonPageColors.kt` 的 `shadowElevation` 计算里：

```kotlin
val shadowElevation = when {
    transparentNavBar -> 0.dp
    config.style == TopBarConfig.STYLE_REGULAR && config.cornerScale != 0f -> 0.dp  // ← 根因
    alphaPercent < 100 -> 0.1.dp
    else -> with(LocalDensity.current) { context.elevation.toDp() }
}
```

`STYLE_REGULAR && cornerScale != 0f` 分支会返回 `0.dp`。默认 `cornerScale = 1f`（见 `TopBarConfig.defaultConfig` / `resolveCornerScale`），因此**凡 regular 风格（且带圆角）的顶栏阴影恒为 0**。这就是用户看到的「阴影没了」。

### 问题 2：阅读记录/关于界面顶栏颜色不对

这些页面仍使用旧 API `pageTopBarContainerColor()`（`CommonPageColors.kt:278` 返回裸 `MaterialTheme.colorScheme.secondary`），或硬编码 `MaterialTheme.colorScheme.secondary`，未接入 `TopBarConfig` 的动态背景色 / 圆角 / 壁纸 / 透明度，导致顶栏颜色与启用主题背景的页面不一致。

## 统一迁移模式（所有 TopAppBar 目标态）

对每个目标文件，将老样式：

```kotlin
val topBarColor = pageTopBarContainerColor()  // 或 MaterialTheme.colorScheme.secondary
TopAppBar(
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = topBarColor,
        scrolledContainerColor = topBarColor,
        navigationIconContentColor = MaterialTheme.colorScheme.onSecondary,
        titleContentColor = MaterialTheme.colorScheme.onSecondary,
        actionIconContentColor = MaterialTheme.colorScheme.onSecondary
    ), ...
)
```

替换为新样式：

```kotlin
val topBarColors = pageTopBarColors()
TopAppBar(
    modifier = Modifier.pageTopBarBackground(topBarColors),
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
        navigationIconContentColor = topBarColors.contentColor,
        titleContentColor = topBarColors.contentColor,
        actionIconContentColor = topBarColors.contentColor
    ), ...
)
```

> **Dialog 半透明浮层注意事项：** `pageTopBarBackground` 内部依赖 `context.transparentNavBar`（`CommonPageColors.kt` 通过 `TopBarConfig` / `transparentNavBar` 计算 `alphaPercent`）。在 Dialog / Surface 浮层内此逻辑可能不符合预期。若实测异常，Dialog 场景改为用 `pageTopBarColors().containerColor` 直填 `containerColor`，不加 `pageTopBarBackground` modifier。**执行时需编译验证并通过真机/预览确认，再调整。**

## 文件结构

### 修改（阴影修复）

- `app/src/main/java/io/legado/app/ui/theme/CommonPageColors.kt:81-86`

### 迁移（旧 API `pageTopBarContainerColor` 页面级）

- `app/src/main/java/io/legado/app/ui/about/AboutActivity.kt`（Activity，有背景兜底）
- `app/src/main/java/io/legado/app/ui/book/readRecord/ReadRecordScreen.kt`（两个 TopAppBar）
- `app/src/main/java/io/legado/app/ui/book/readRecord/BookReadRecordActivity.kt`（用 `surface` 色）
- `app/src/main/java/io/legado/app/ui/book/readRecord/ReadRecordColors.kt`（删除 `readRecordTopBarContainerColor` 别名）

### 迁移（旧 API 内嵌片段 / Screen）

- `app/src/main/java/io/legado/app/ui/debuglog/DebugLogScreen.kt`（内嵌片段，无背景兜底；`pageTopBarBackground` 自带背景，安全）
- `app/src/main/java/io/legado/app/ui/book/source/check/CheckSourceScreen.kt`

### 迁移（Dialog 骨架 / 对话框）

- `app/src/main/java/io/legado/app/ui/widget/components/dialog/AppDialogScaffold.kt`
- `app/src/main/java/io/legado/app/ui/widget/components/dialog/MultiSelectDialog.kt`
- `app/src/main/java/io/legado/app/ui/book/read/TextMenuConfigDialog.kt`（×2 dialog）

### 迁移（硬编码 `secondary` 调试页）

- `app/src/main/java/io/legado/app/ui/debug/DebugToolsScreen.kt`
- `app/src/main/java/io/legado/app/ui/debug/TimestampConvertScreen.kt`
- `app/src/main/java/io/legado/app/ui/debug/RegexTestScreen.kt`
- `app/src/main/java/io/legado/app/ui/debug/PingTestScreen.kt`
- `app/src/main/java/io/legado/app/ui/debug/HttpDebugScreen.kt`
- `app/src/main/java/io/legado/app/ui/debug/EncodeToolsScreen.kt`
- `app/src/main/java/io/legado/app/ui/debug/CurlTestScreen.kt`
- `app/src/main/java/io/legado/app/ui/upload/DirectLinkUploadScreen.kt`

---

## 任务 1：修复阴影逻辑

**文件：** `app/src/main/java/io/legado/app/ui/theme/CommonPageColors.kt:81-86`

**问题：** `STYLE_REGULAR && cornerScale != 0f` 分支强制阴影为 `0.dp`。

**步骤：**

- [ ] **步骤 1：修改 `shadowElevation` 计算**

移除 `STYLE_REGULAR && cornerScale != 0f` 置零分支：

```kotlin
val shadowElevation = when {
    transparentNavBar -> 0.dp
    alphaPercent < 100 -> 0.1.dp
    else -> with(LocalDensity.current) { context.elevation.toDp() }
}
```

> 设计说明：`transparentNavBar` 保持 `0.dp`（沉浸无边框场景不需要阴影）；半透明背景（`alphaPercent < 100`）用 `0.1.dp` 轻微阴影以避免干扰壁纸透出；不透明时用真实 `context.elevation`。`.shadow(elevation, RoundedCornerShape(bottom))` + `.clip(shape)` 组合可正确渲染圆角阴影，无冲突。

- [ ] **步骤 2：编译验证**

运行：

```
./gradlew :app:compileAppMaxDebugKotlin
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/io/legado/app/ui/theme/CommonPageColors.kt
git commit -m "fix(顶栏): 修复 regular 风格圆角顶栏阴影被强制置零的问题"
```

---

## 任务 2：迁移页面级 Activity/Screen 顶栏（About / ReadRecord / BookReadRecord）

### 2a. AboutActivity.kt

**文件：** `app/src/main/java/io/legado/app/ui/about/AboutActivity.kt:43,78-113`

- [ ] **步骤 1：改 import 与颜色来源**

删除 `import io.legado.app.ui.theme.pageTopBarContainerColor`（line 43），改为：

```kotlin
import io.legado.app.ui.graph...  // 无需；用现成的
import io.legado.app.ui.theme.pageTopBarBackground
import io.legado.app.ui.theme.pageTopBarColors
import androidx.compose.ui.graphics.Color
```

在 `AboutScreen` 函数体（line 78）把 `val topBarColor = pageTopBarContainerColor()` 改为：

```kotlin
val topBarColors = pageTopBarColors()
```

- [ ] **步骤 2：改 TopAppBar colors**

`TopAppBar(colors = TopAppBarDefaults.topAppBarColors(...))` 改为：

```kotlin
TopAppBar(
    modifier = Modifier.pageTopBarBackground(topBarColors),
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
        navigationIconContentColor = topBarColors.contentColor,
        titleContentColor = topBarColors.contentColor,
        actionIconContentColor = topBarColors.contentColor
    ), ...
)
```

> 需确认 `Modifier` 已 import。

### 2b. ReadRecordScreen.kt

**文件：** `app/src/main/java/io/legado/app/ui/book/readRecord/ReadRecordScreen.kt:77,204,238`

- [ ] **步骤 3：改颜色来源**

line 77 `val topBarColor = readRecordTopBarContainerColor()` → `val topBarColors = pageTopBarColors()`（删除 `topBarColor` 变量）。加 import：`pageTopBarColors`、`pageTopBarBackground`、`androidx.compose.ui.graphics.Color`。

- [ ] **步骤 4：改两个 TopAppBar**

line 204（选择模式）与 line 238（正常模式）：把 `colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarColor, scrolledContainerColor = topBarColor, ... onSecondary ...)` 改为 `modifier = Modifier.pageTopBarBackground(topBarColors)` + `containerColor = Color.Transparent` + `contentColor = topBarColors.contentColor`。

### 2c. BookReadRecordActivity.kt

**文件：** `app/src/main/java/io/legado/app/ui/book/readRecord/BookReadRecordActivity.kt:119-141`

- [ ] **步骤 5：改 TopAppBar**

`containerColor = MaterialTheme.colorScheme.surface` → `modifier = Modifier.pageTopBarBackground(topBarColors)` + `Color.Transparent` + `contentColor`（需先在函数内 `val topBarColors = pageTopBarColors()` 并加 import）。

### 2d. ReadRecordColors.kt

**文件：** `app/src/main/java/io/legado/app/ui/book/readRecord/ReadRecordColors.kt:17`

- [ ] **步骤 6：删除废弃别名**

删除 `readRecordTopBarContainerColor()` 函数与 `import pageTopBarContainerColor`（line 13），确认无其他引用后移除 import。

- [ ] **步骤 7：编译验证**

```
./gradlew :app:compileAppMaxDebugKotlin
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 8：Commit**

```bash
git add app/src/main/java/io/legado/app/ui/about/AboutActivity.kt \
        app/src/main/java/io/legado/app/ui/book/readRecord/ReadRecordScreen.kt \
        app/src/main/java/io/legado/app/ui/book/readRecord/BookReadRecordActivity.kt \
        app/src/main/java/io/legado/app/ui/book/readRecord/ReadRecordColors.kt
git commit -m "refactor(顶栏): 阅读记录/关于等页面级界面接入统一 TopBarConfig 渲染"
```

---

## 任务 3：迁移内嵌片段 / Screen 顶栏（DebugLog / CheckSource）

### 3a. DebugLogScreen.kt

**文件：** `app/src/main/java/io/legado/app/ui/debuglog/DebugLogScreen.kt:81,131,158`

- [ ] **步骤 1：改颜色来源与 TopAppBar**

line 131 `val topBarColor = pageTopBarContainerColor()` → `val topBarColors = pageTopBarColors()`；line 158 `TopAppBar` 用 `Modifier.pageTopBarBackground(topBarColors)` + `Color.Transparent` + `contentColor`。删除旧 import，加 `pageTopBarColors` / `pageTopBarBackground` / `Color`。

> 该页是内嵌片段（ComposeView，无背景兜底），`pageTopBarBackground` 自带完整背景（含壁纸/纯色），恰好适用于无外层兜底场景，安全。

### 3b. CheckSourceScreen.kt

**文件：** `app/src/main/java/io/legado/app/ui/book/source/check/CheckSourceScreen.kt:59,411,419`

- [ ] **步骤 2：改颜色来源与 TopAppBar**

line 411 `val containerColor = pageTopBarContainerColor()` → 删除/替换。line 419 `TopAppBar` 用 `pageTopBarColors()` + `pageTopBarBackground` + `contentColor`。`CheckSourceTopBar` 内的 `pageSecondaryTextColor()` 副标题色保留。

- [ ] **步骤 3：编译验证 + Commit**

```
./gradlew :app:compileAppMaxDebugKotlin
```

```
git add app/src/main/java/io/legado/app/ui/debuglog/DebugLogScreen.kt \
        app/src/main/java/io/legado/app/ui/book/source/check/CheckSourceScreen.kt
git commit -m "refactor(顶栏): 调试日志/检测源界面接入统一顶栏渲染"
```

---

## 任务 4：迁移 Dialog 骨架与对话框

> Dialog 内 `pageTopBarBackground` 依赖 `transparentNavBar` / `TopBarConfig`，可能不符合预期。先用 `pageTopBarColors().containerColor` 直填 `containerColor` 的安全方案，实测异常再升级为 modifier。

### 4a. AppDialogScaffold.kt

**文件：** `app/src/main/java/io/legado/app/ui/widget/components/dialog/AppDialogScaffold.kt:23,34,46`

- [ ] **步骤 1：改默认参数与图标色**

line 34 默认参数 `topBarContainerColor: Color = pageTopBarContainerColor()` → 改为在函数体内计算。若保留参数，默认改为 `pageTopBarColors().containerColor`；line 46 `titleContentColor` 由 `onSurface` 改为 `pageTopBarColors().contentColor`。

> 注意：`topBarContainerColor` 是参数，为了不破坏调用方，可保留参数签名，但默认值改新色源。若调用方传值则不动调用方。

- [ ] **步骤 2：编译验证**

```
./gradlew :app:compileAppMaxDebugKotlin
```

### 4b. MultiSelectDialog.kt

**文件：** `app/src/main/java/io/legado/app/ui/widget/components/dialog/MultiSelectDialog.kt:20,73`

- [ ] **步骤 3：改颜色**

line 73 `val topBarColor = pageTopBarContainerColor()` → `val topBarColor = pageTopBarColors().containerColor`。改 import。

### 4c. TextMenuConfigDialog.kt

**文件：** `app/src/main/java/io/legado/app/ui/book/read/TextMenuConfigDialog.kt:36,79,396`

- [ ] **步骤 4：改颜色（两处）**

line 79 与 line 396 的 `val topBarColor = pageTopBarContainerColor()` → `val topBarColor = pageTopBarColors().containerColor`。改 import。

- [ ] **步骤 5：编译验证 + Commit**

```
./gradlew :app:compileAppMaxDebugKotlin
```

```
git add app/src/main/java/io/legado/app/ui/widget/components/dialog/AppDialogScaffold.kt \
        app/src/main/java/io/legado/app/ui/widget/components/dialog/MultiSelectDialog.kt \
        app/src/main/java/io/legado/app/ui/book/read/TextMenuConfigDialog.kt
git commit -m "refactor(顶栏): 对话框顶栏容器色统一取自 TopBarConfig"
```

---

## 任务 5：迁移硬编码 secondary 调试页

**文件（8 个）：**

- `app/src/main/java/io/legado/app/ui/debug/TimestampConvertScreen.kt:94`
- `app/src/main/java/io/legado/app/ui/debug/RegexTestScreen.kt:294`
- `app/src/main/java/io/legado/app/ui/debug/PingTestScreen.kt:72`
- `app/src/main/java/io/legado/app/ui/debug/HttpDebugScreen.kt:198`
- `app/src/main/java/io/legado/app/ui/debug/EncodeToolsScreen.kt:61`
- `app/src/main/java/io/legado/app/ui/debug/DebugToolsScreen.kt:88`
- `app/src/main/java/io/legado/app/ui/debug/CurlTestScreen.kt:158`
- `app/src/main/java/io/legado/app/ui/upload/DirectLinkUploadScreen.kt:103`

- [ ] **步骤 1：逐文件替换**

每个文件把 `containerColor = MaterialTheme.colorScheme.secondary` / `scrolledContainerColor = MaterialTheme.colorScheme.secondary` 与 `onSecondary` 图标色，改为 `val topBarColors = pageTopBarColors()` + `Modifier.pageTopBarBackground(topBarColors)` + `Color.Transparent` + `topBarColors.contentColor`。加对应 import。

- [ ] **步骤 2：编译验证 + Commit**

```
./gradlew :app:compileAppMaxDebugKotlin
```

```
git add app/src/main/java/io/legado/app/ui/debug/ app/src/main/java/io/legado/app/ui/upload/DirectLinkUploadScreen.kt
git commit -m "refactor(顶栏): 调试工具各页面接入统一顶栏渲染"
```

---

## 任务 6：最终验证与收尾

- [ ] **步骤 1：全量编译**

```
./gradlew :app:compileAppMaxDebugKotlin
```

预期：BUILD SUCCESSFUL 无告警（尤其无 unused import）。

- [ ] **步骤 2：全仓搜索残留旧 API**

用 grep 确认没有残留 `pageTopBarContainerColor`（除 `CommonPageColors.kt:278` 定义本身，若决定保留兼容则保留）与硬编码 `colorScheme.secondary` 顶栏。

- [ ] **步骤 3：最终状态确认**

`git status` / `git log --oneline -8` 确认分组 commit 齐全、无未提交改动。

---

## 自检清单

- **规格覆盖度：** 阴影逻辑（任务1）、旧 API 页面级（任务2）、内嵌片段（任务3）、Dialog（任务4）、硬编码调试页（任务5）、编译+commit（任务6）全覆盖。
- **占位符扫描：** 无「待定/TODO」占位；每步含具体文件/行号/代码。
- **类型一致性：** 所有迁移统一用 `pageTopBarColors()` + `pageTopBarBackground` + `contentColor`；Dialog 场景用 `pageTopBarColors().containerColor`。

## 风险 / 待实测

1. **Dialog 半透明浮层：** `pageTopBarBackground` 在 Dialog/Surface 内的表现需真机/预览确认；异常则降级为 containerColor 直填（已在任务4 采用该安全方案）。
2. **阴影视觉变化：** regular 风格也显示阴影后外观会变，需确认符合预期。
3. **ReadRecordScreen** 需保留选择模式的 `topBarColors` 复用，避免重复计算。
