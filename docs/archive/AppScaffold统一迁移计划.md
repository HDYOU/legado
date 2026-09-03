# 计划：全量 Compose 页面统一到 AppScaffold

> 范围：项目内全部页面级 Compose 界面的 `Scaffold` 收敛
> 目标：消除裸 `Scaffold`，统一由 `AppScaffold` 承载，消除配置漂移

**状态：第 1-4 步全部完成（2026-09-03）。** 裸 `Scaffold` 仅剩 3 处主动排除的内嵌 `ComposeView` 片段（Homepage / BookshelfTagManage / DebugLog）。

- 验证：`:app:compileAppMaxDebugKotlin` 通过
- 人工回归：第 1-3 步已打包验证正常；第 4 步（调试工具 7 页）待回归
- 导航栏口径：滚动列表统一用 `LazyColumn`/`contentPadding` 底部补 `navigationBarBottomInset`（内容铺满、最后一项可滚上来完整可点）；整页 `verticalScroll` 列与固定控件仍用 `navigationBarsPadding()`。helper 见 `AppScaffold.kt`

## 背景

`377359f feat(顶栏): 新增 AppScaffold 并统一 7 个页面 Scaffold 配置对齐精准管理` 落地了 `AppScaffold`，但只覆盖了精准管理下的 7 个子界面，项目里仍有 **20 处裸 `Scaffold`**。

规范本身要求使用通用脚手架（`docs/project-rules/compose/migration-review.md:64`，§14.2 Reviewer 对照，任一 ❌ 打回）：

> 新 Screen 是否套了 `ConfigManageScaffold` / `AppScaffold` 等通用脚手架？还是裸 `Scaffold`？

因此"全量统一"是规范要求，不是可选优化。

## 目标 / 非目标

**目标**

- `AppScaffold` 补齐能力，能覆盖全部页面级场景
- 17 处适用的裸 `Scaffold` 全部改用 `AppScaffold`
- 明确"页面级"边界，给 review 留下可判定依据

**非目标**（另立任务，本次不做）

- **顶栏配色对齐**：把剩余页面从降级配色 `pageTopBarContainerColor()` 改为 `pageTopBarColors()` + `Modifier.pageTopBarBackground()`。换 `AppScaffold` **不会**改善顶栏，二者是不同维度
- 3 处内嵌 `ComposeView` 片段的替换（见下表）
- 组件目录归位（`ui/widget/components/` → 规范规划的 `ui/components/`）

## 关键约束：导航栏口径

**要"内容铺满"，不要固定留白带。**

因此 `AppScaffold` 的 `contentWindowInsets = WindowInsets(0, 0, 0, 0)` **保留不动**，"适配 insets"由内容侧自己完成：

| 页面类型              | 处理方式                                              | 效果                                                   |
| --------------------- | ----------------------------------------------------- | ------------------------------------------------------ |
| 滚动列表              | `LazyColumn` 加 `contentPadding`（底部 = 导航条高度） | 内容滚动时穿过导航栏（铺满），最后一项能滚上来完整可点 |
| 底部有固定按钮/输入框 | 该元素加 `Modifier.navigationBarsPadding()`           | 控件不被导航条压住                                     |

注意 `contentWindowInsets = 0` **不等于**"顶栏延伸到状态栏"——顶栏是否贯通状态栏由 `TopAppBar` 自身的 `windowInsets` 决定，与此参数无关（现有 KDoc 此处描述错误，第 1 步修正）。

依据：`docs/project-rules/api-compat-rules.md:58` Edge-to-Edge 强制（targetSdk 35+），所有页面必须适配 insets。

## 盘点结果

### 可替换（17 处）

宿主均为 `BaseComposeActivity` 或手动调用 `setLegadoContent`，即外层有 `LegadoBackgroundBox` 背景兜底，`containerColor = Transparent` 安全。

| #   | 文件:行                                                 | 宿主                                                                                                     | 备注                             |
| --- | ------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- | -------------------------------- |
| 1   | `ui/config/widget/ConfigManageScaffold.kt:51`           | 被 `ThemeManageScreen.kt:93`、`ShareNoteTemplateManageScreen.kt:55` 复用（宿主均 `BaseComposeActivity`） | **标准件，第 2 步**              |
| 2   | `ui/about/AboutActivity.kt:79`                          | `AboutActivity : BaseComposeActivity`                                                                    |                                  |
| 3   | `ui/book/cacheSelector/BookCacheSelectorScreen.kt:81`   | `BookCacheSelectorActivity : BaseComposeActivity`                                                        | 已有 `bottomBar`（L135），已支持 |
| 4   | `ui/book/source/check/CheckSourceScreen.kt:85`          | `CheckSourceActivity : BaseComposeActivity`                                                              |                                  |
| 5   | `ui/config/covergallery/CoverGalleryScreen.kt:247`      | `CoverGalleryActivity : BaseComposeActivity`                                                             |                                  |
| 6   | `ui/config/coverhtml/CoverHtmlCodeScreen.kt:214`        | `CoverHtmlActivity : BaseComposeActivity`                                                                | 注意底部输入区                   |
| 7   | `ui/config/coverhtml/CoverHtmlTemplateListScreen.kt:57` | 同上                                                                                                     |                                  |
| 8   | `ui/book/readRecord/ReadRecordScreen.kt:199`            | `ReadRecordActivity : BaseComposeActivity`                                                               | **唯一用 `snackbarHost` 的页面** |
| 9   | `ui/book/readRecord/BookReadRecordActivity.kt:118`      | `BookReadRecordActivity : BaseComposeActivity`                                                           |                                  |
| 10  | `ui/upload/DirectLinkUploadScreen.kt:98`                | `DirectLinkUploadActivity : AppCompatActivity` + 手动 `setLegadoContent`                                 |                                  |
| 11  | `ui/debug/DebugToolsScreen.kt:84`                       | `DebugToolsActivity : BaseComposeActivity`                                                               | 底部有固定 UI                    |
| 12  | `ui/debug/HttpDebugScreen.kt:195`                       | `HttpDebugActivity : BaseComposeActivity`                                                                | 同上                             |
| 13  | `ui/debug/CurlTestScreen.kt:155`                        | `CurlTestActivity : BaseComposeActivity`                                                                 | 同上                             |
| 14  | `ui/debug/PingTestScreen.kt:69`                         | `PingTestActivity : BaseComposeActivity`                                                                 | 同上                             |
| 15  | `ui/debug/RegexTestScreen.kt:291`                       | `RegexTestActivity : BaseComposeActivity`                                                                | 同上                             |
| 16  | `ui/debug/EncodeToolsScreen.kt:58`                      | `EncodeToolsActivity : BaseComposeActivity`                                                              | 同上                             |
| 17  | `ui/debug/TimestampConvertScreen.kt:91`                 | `TimestampConvertActivity : BaseComposeActivity`                                                         | 同上                             |

### 不替换（3 处）

嵌在 View 体系的 `ComposeView` 中，**非页面级**，外层无 `LegadoBackgroundBox` 兜底，改透明会露出窗口默认背景。

| 文件:行                                            | 调用方                             | 宿主                                                             |
| -------------------------------------------------- | ---------------------------------- | ---------------------------------------------------------------- |
| `ui/main/homepage/HomepageScreen.kt:196`           | `HomepageFragment.kt:67`           | `MainActivity : VMBaseActivity`（View）                          |
| `ui/main/bookshelf/BookshelfTagManageScreen.kt:83` | `BookshelfTagManageActivity.kt:29` | `BaseActivity<ActivityBookshelfTagManageBinding>`（ViewBinding） |
| `ui/debuglog/DebugLogScreen.kt:154`                | `DebugLogPanelDialog.kt:210`       | 悬浮面板，`ComposeView` 直挂 decorView                           |

### 已接入（7 处，上一轮完成，本次不动）

`FileManageScreen:84`、`DownloadManageScreen:63`、`UrlRecordScreen:124`、`SourceRecycleBinScreen:112`、`StorageManageScreen:100`、`PermissionManageScreen:33`、`ModuleStatusScreen:60`

## 实施步骤

### 第 1 步 · AppScaffold 补能力 + 修文档

文件：`ui/widget/components/AppScaffold.kt`

1. 补 `snackbarHost: @Composable () -> Unit = {}` 参数（服务 `ReadRecordScreen.kt:344`）
2. 修正 KDoc 中 `contentWindowInsets` 的错误描述（真实作用是影响 **content** 的左右/底部系统栏内边距）
3. KDoc 写明适用范围：**页面级**（宿主为 `BaseComposeActivity` / `setLegadoContent`），明确排除内嵌 `ComposeView` 片段，给 §14.2 review 留判定依据
4. 清理第 22 行无 `*` 前缀的笔记式注释（若尚未清理）

**影响**：纯增量，新参数带默认值，已接入的 7 页零行为变化。
**无需补 `floatingActionButton`**：全项目 Compose 侧零使用。

### 第 2 步 · ConfigManageScaffold 复用 AppScaffold

把 `ConfigManageScaffold.kt:51-55` 的裸 `Scaffold` 换成 `AppScaffold`，保留其自身的顶栏/多选底栏/返回逻辑。

**影响**：`ThemeManageScreen`、`ShareNoteTemplateManageScreen` 两个页面。
**风险**：零。两者配置本就一致（均为 `Color.Transparent` + `WindowInsets(0,0,0,0)`），逻辑等价、逐像素相同。
**收益**：标准件与实现合一，防止后续漂移。

### 第 3 步 · 9 个业务页替换

第 1-10 项中除 `ConfigManageScaffold` 外的 9 处：About、BookCacheSelector、CheckSource、CoverGallery、CoverHtmlCode、CoverHtmlTemplateList、ReadRecord、BookReadRecord、DirectLinkUpload。

逐个替换，每处检查底部是否需要补 padding（见"每处改动的行为影响"）。

### 第 4 步 · 调试工具 7 页（最后做）

底部几乎都有输入框 + 按钮，被导航条压住的风险最高，需逐页加 `navigationBarsPadding()`。建议单独提交，也可单独评估是否值得做。

## 每处改动的行为影响

替换后统一发生三件事：

1. **背景透出**：`containerColor` → `Transparent`，主题背景图/壁纸开始可见。宿主均安全，但**视觉会变化**，需人工确认
2. **底部内边距消失**：`contentWindowInsets` → 0，按"导航栏口径"一节的分类处理
3. **顶栏不变**：`AppScaffold` 不碰 `topBar`，顶栏行为保持现状

⚠️ 换 `AppScaffold` ≠ 顶栏对齐。第 1-17 项中多数仍用旧降级配色（如 `ReadRecordScreen.kt:205` 的 `topBarColor` + `colorScheme.onSecondary`），换脚手架不会改善其顶栏。

## 验证方式

```bash
./gradlew.bat :app:compileAppMaxDebugKotlin   # 每批改完执行
./gradlew lint                                 # CI 把关
```

人工回归，每页过一遍：

- 明暗主题、E-ink 模式各抽查
- 三键导航 / 手势导航各抽查
- 三个必看项：**背景图是否透出**、**列表最后一项能否完整点击**、**底部按钮是否被导航条压住**

## 提交拆分

```
refactor(顶栏): AppScaffold 补充 snackbarHost 参数并明确适用范围
refactor(顶栏): ConfigManageScaffold 复用 AppScaffold
refactor(顶栏): 9 个业务页统一改用 AppScaffold
refactor(顶栏): 调试工具 7 页改用 AppScaffold 并适配导航栏
```

## 遗留 / follow-up

- 顶栏配色对齐（剩余页面改用 `pageTopBarColors()` + `pageTopBarBackground()`），另立任务
- `pageTopBarContainerColor()` 全局下架（约 14 处在用，属另一范围）
- 组件目录归位：`AppScaffold.kt` 现位于 `ui/widget/components/`，规范 `structure.md:18` 规划在 `ui/components/`（标注为"目标态，首次落地时建"）
- 已接入的 7 页底部同样缺少导航条内边距，与精准管理主界面"一致地缺失"，需单独确认主界面的期望行为后统一处理
