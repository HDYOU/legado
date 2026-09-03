# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

阅读Max (legado_Plus) — an Android e-book reader app forked from Legado. Supports custom book sources with user-defined rules (Jsoup selectors + Rhino JS), RSS subscriptions, local TXT/EPUB reading, and an embedded HTTP/WebSocket server for remote control.

## Build Commands

Uses Gradle wrapper (`gradlew.bat` on Windows). JDK 17 required.

```bash
# Debug build (default flavor: appMax)
./gradlew assembleDebug

# Release build (ProGuard + resource shrinking enabled)
./gradlew assembleRelease

# Specific flavor builds
./gradlew assembleAppMaxDebug       # appMax (io.legado.app.yuedu, coexistence)
./gradlew assembleAppLegacyRelease  # appLegacy (io.legado.app, same as original)
./gradlew assembleAppSDebug         # appS (io.legado.app.yuedu.a)

# Install to device
./gradlew installDebug
./gradlew installAppMaxDebug

# Tests
./gradlew test                      # Unit tests
./gradlew connectedAndroidTest      # Instrumented tests

# stop
./gradlew stop

# Grammar Test
./gradlew.bat :app:compileAppMaxDebugKotlin

# Lint
./gradlew lint

# Download Cronet native libs (required before first build)
./gradlew app:downloadCronet

# 查看DSL语法警告
# Windows
gradlew assembleDebug --warning-mode all
# Mac/Linux
./gradlew assembleDebug --warning-mode all
```

### Web Frontend (modules/web)

The embedded HTTP server's frontend is a Vue 3 + Vite app in `modules/web/`. It builds to `app/src/main/assets/web/vue/`.

```bash
cd modules/web
pnpm install        # requires Node >= 20, pnpm >= 9
pnpm dev            # local dev server with HMR
pnpm build          # production build + syncs to assets/web/vue/
pnpm lint:fix       # eslint auto-fix
pnpm format         # prettier
```

## Architecture

MVVM pattern with AndroidViewModel + ViewBinding + Coroutines.

### Base Classes (`io.legado.app.base`)

- `BaseActivity<VB>` — all Activities extend this. Manages theming, system bars, view binding. Override `observeLiveBus()` for event subscriptions (auto-cleaned on destroy).
- `VMBaseActivity<VB, VM>` — adds abstract `viewModel` property.
- `BaseViewModel` — extends `AndroidViewModel`. Key method: `execute { }` returns a `Coroutine<T>` with chainable `.onSuccess`, `.onError`, `.onFinally`. Default context is `Dispatchers.IO`, callbacks on `Dispatchers.Main`.

### Key Patterns

- **Coroutine helper**: `BaseViewModel.execute()` wraps `Coroutine.async()`. Use this instead of raw `viewModelScope.launch`.
- **Event bus**: `LiveEventBus` for cross-component events. Subscribe via `observeEvent<T>(key) { ... }` in `observeLiveBus()`.
- **Database**: Room (`AppDatabase` v100), singleton at `appDb`. DAOs in `data/`, entities in `data/entities/`. Uses KSP (not kapt).
- **Book source rules**: Rhino JS engine (`:modules:rhino` module) evaluates user-defined rules. The `analyzeRule` package in `model/` handles rule parsing.
- **Singletons in model/**: `ReadBook`, `CacheBook`, `AudioPlay` manage global reading state.
- **Config packages**: `TopBarConfig` and `BubblePackageManager` store configs as file system directories (JSON + assets like wallpapers/icons), not SharedPreferences. `NavigationBarConfig` uses SharedPreferences. `ApplicationThemeManager` combines all sub-configs into exportable/importable theme packages (zip).

### Modules

The project has three library modules in `modules/`:

- `modules/book` — fork of epublib (EPUB parsing), package `me.ag2s.epublib`
- `modules/rhino` — fork of Mozilla Rhino JS engine, package `com.script`. Evaluates user-defined book source rules at runtime.
- `modules/web` — Vue 3 frontend for the embedded HTTP/WebSocket server (see above)

### Source Layout

`app/src/main/java/io/legado/app/`:

- `ui/` — Activities/Fragments grouped by feature (book/, rss/, source/, config/, debuglog/, image/)
- `model/` — domain logic (WebBook for HTTP fetching, analyzeRule for rule engine, ParagraphBubbleRenderer, BookCover)
- `data/` — Room DB, DAOs, repositories
- `help/` — helpers (config managers for theme/navbar/topbar/bubble, http client, coroutine utilities, source management)
- `lib/theme/` — theme utilities (accent colors, typography, corners, page colors, TitleBar config extensions)
- `utils/` — Kotlin extensions (~100+ files)
- `web/` — embedded NanoHTTPD server + WebSocket endpoints

### Compose Usage

Jetpack Compose (Material3, BOM 2026.08.00) is used for newer UI surfaces (e.g. debug log panel). Traditional View system (ViewBinding + XML layouts) is used for most existing screens. Both coexist — ComposeViews can be overlaid on View-based Activities.

Compose 规范拆分为 8 个文件，位于 `docs/project-rules/compose/`：

- [`compose/structure.md`](docs/project-rules/compose/structure.md) — 目录结构、命名、API 契约、通用脚手架
- [`compose/state-events.md`](docs/project-rules/compose/state-events.md) — UiState / Event 流、Dialog/BottomSheet 渲染
- [`compose/theme-styles.md`](docs/project-rules/compose/theme-styles.md) — 颜色、尺寸、图片加载、字体、字符串、动画
- [`compose/performance.md`](docs/project-rules/compose/performance.md) — Recomposition 防范、副作用、图片内存
- [`compose/navigation-preview.md`](docs/project-rules/compose/navigation-preview.md) — 导航规范、Preview 规范
- [`compose/accessibility.md`](docs/project-rules/compose/accessibility.md) — 无障碍（contentDescription / semantics / 触控目标）
- [`compose/testing.md`](docs/project-rules/compose/testing.md) — 测试分层、runTest + Turbine 模板、CI 接入
- [`compose/migration-review.md`](docs/project-rules/compose/migration-review.md) — 老代码迁移三阶段、Review Checklist（CI 硬卡 + 人工项）、典型违规示例

## 项目级规范（必读）

项目级强制规范库位于 `docs/project-rules/`，索引与领域覆盖矩阵见 [`docs/project-rules/README.md`](docs/project-rules/README.md)。写代码前先按"什么时候必须读"对照索引；改动代码后主动回看相关规范是否需要同步更新（规范跟着代码走，pre-commit 的 help-doc-sync 钩子会拦截"改了受管代码却没改对应文档"的提交）；规范与实现冲突时以源码为准并回头修规范。

- **协程**：本项目使用自研链式协程包装（`BaseViewModel.execute` → `help/coroutine/Coroutine`）。使用协程前必读 [`docs/project-rules/coroutine-rules.md`](docs/project-rules/coroutine-rules.md)，其中包含 `execute` 链的时序坑、Scope 规则、Flow 位置与反面示例。
- **数据层（Repository）**：[`docs/project-rules/repository-rules.md`](docs/project-rules/repository-rules.md)，新增数据访问逻辑必须遵循。
- **API 兼容**：[`docs/project-rules/api-compat-rules.md`](docs/project-rules/api-compat-rules.md)。调用高于 minSdk 23 的 API、引入新依赖、发版前必读（SDK 分支写法、desugaring 边界、16KB 对齐等 targetSdk 37 红线）。
- **事件总线**：[`docs/project-rules/live-event-bus-rules.md`](docs/project-rules/live-event-bus-rules.md)。新增跨组件事件、在 LiveEventBus 与 Compose `Channel<Event>` 之间选型时必读。
- **架构与设计说明**：[`docs/architecture/`](docs/architecture/) 存放长期有效的模块架构、设计方案、技术笔记（Web 服务架构、高亮规则架构、Cookie 管理设计等）。想了解某个模块"现在是怎么设计的"先翻这里；一次性改造方案在 `docs/archive/`，两者不要混。

### 计划/方案文档的收尾

`docs/` 根目录不放散文档，只保留各规范子目录。任务收尾（实现 + 验证 + code-review + 提交）时，同一批处理本次产生的方案文档，不留悬空计划：

- **一次性方案 / 计划 / 根因分析**（文件名含「方案」「计划」「分析」「评估」）：实现落地后 `git mv` 到 `docs/archive/`，沿用原文件名，不加日期前缀。
- **结论已沉淀进代码和规范**的：直接删除，避免同一事实两处维护、日后文档与代码不同步。
- **长期有效的约定 / checklist**：下沉到 `docs/project-rules/`，并在 `docs/project-rules/README.md` 索引登记。
- **长期有效的架构说明**（模块架构、设计方案、技术笔记、功能说明）：放入 `docs/architecture/`，并在文件开头写明适用范围与是否仍然有效。

判断依据：下次有人问"这块怎么做的"时还会不会来读这份文档——会，就进 `docs/architecture/` 或下沉成规范；不会，就归档或删除。

> **代码永远比文档准确**：文档只记录某一个时刻的状态，会随迭代腐化。任何时候发现文档与代码不符，一律**以代码为准**，并顺手修正文档（或标注"已过时，见 xxx"）。文档是导航不是契约——用它找方向，别用它下结论。

## Coding Conventions

- Kotlin 代码风格遵循 Google Android Style Guide
- 命名规则：
  - Activities: `XxxActivity`
  - ViewModels: `XxxViewModel`
  - Fragments: `XxxFragment`
- 日志使用统一的 tag 格式：`AppTag.xxx`

## Comments

> 注释优先表达**为什么这么做、特殊约束、业务背景**，代码本身负责表达“是什么、怎么做”。

- **类注释**
  - 核心/复杂类（单例、引擎、解析器、管理器等）必须补充完整 KDoc。
  - 普通 `Activity` / `Adapter` / `ViewModel` 无需完整 KDoc，仅简短说明核心用途即可。
- **函数注释**
  - 对外公开 API、复杂业务逻辑、有特殊入参/返回值约束的函数，必须写 KDoc。
  - 简单 getter / setter、工具内部私有简单函数，不额外加注释。
- **变量注释**
  - 优先靠命名表达语义，命名清晰则不加行内注释。
  - 仅业务含义隐晦、存在特殊边界约定时才补充注释。
- **注释原则**
  - 写「为什么」，不重复复述代码已经能看出来的「是什么」。
  - 不要把代码逻辑翻译成自然语言。

## Dependency Management

- 所有依赖版本通过 `gradle/libs.versions.toml` 统一管理
- 禁止直接在 `build.gradle` 中硬编码版本号
- 新增依赖需同步更新版本目录文档

## Testing Strategy

这个视情况讨论，因为有时开发环境不允许。

- 单元测试：`app/src/test/`
- 集成测试：`app/src/androidTest/`
- 测试覆盖率要求：核心模块 ≥ 80%
- Mock 框架：Mockk
- 协程测试：kotlinx-coroutines-test
- LeakCanary: `debugImplementation` only — memory leak detection in debug builds.

## Version Catalog

All dependency versions are in `gradle/libs.versions.toml`. In `build.gradle.kts` or `build.gradle`, reference them as `libs.xxx`. Major versions: Kotlin 2.3.10, Hilt 2.59, OkHttp 5.3.2, Room 2.8.4, Coroutines 1.10.2, Compose BOM 2026.08.00.

## Build Variants

Three product flavors in dimension "app":

- `appLegacy` — same package name as original Legado (`io.legado.app`)
- `appMax` — coexistence package (`io.legado.app.yuedu`), the primary development target
- `appS` — another coexistence package (`io.legado.app.yuedu.a`)

SDK levels: minSdk 23, targetSdk 37, compileSdk 37, JVM 17 toolchain. coreLibraryDesugaring is enabled — JVM 17 syntax (records, text blocks, List.of) works down to API 23.
Both build types set an applicationIdSuffix (`.debug` / `.release`), so the installed package is e.g. `io.legado.app.yuedu.debug`, not the bare flavor id.

Release builds: minifyEnabled + shrinkResources + ProGuard (`app/proguard-rules.pro`, `app/cronet-proguard-rules.pro`). Debug builds: no minification.

## CI/CD

GitHub Actions in `.github/workflows/`:

- `test.yml` — builds all 3 release flavors on push to main; auto-creates GitHub/Gitee releases with changelog from `updateLog.md`
- `web.yml` — builds the Vue frontend on changes to `modules/web/` and commits the output to `app/src/main/assets/web/vue/`
- `cronet.yml` — updates Cronet native libraries
- `lint.yaml` — runs lint in CI; treat `./gradlew lint` passing as part of "done"

## Conventions

- Annotation processing uses KSP, not kapt.
- `NonTransitiveRClass` is enabled — reference only directly used resources.
- Room schema exports to `$projectDir/schemas` for migration verification.
- Disabled build features: aidl, renderscript, resvalues, shaders. buildConfig is explicitly enabled (Cronet version fields); do not assume BuildConfig is absent.
- Architecture documentation in `Structure/` directory (Chinese) covers app startup flow, database schema, reading flow, event bus, and module dependencies.

## Git Commit 规范

Conventional Commits 中文适配，husky + commitlint 自动校验。

格式：`<type>(<scope>): <subject>`，type 用英文（feat/fix/docs/refactor/perf/test/chore/ci/revert/style），scope 和 subject 用中文，subject 为动宾短语不超 100 字符。

交互式提交：`npm run commit`。不合规提交会被自动拦截。

**新人首次使用需在项目根目录运行 `npm install`**，否则 git hook 不会生效。

详细规范与常见问题见 `docs/git-hook/`。

### 帮助文档同步检查（pre-commit 门禁）

改了功能代码但没改对应的帮助文档？这个 hook 会拦下来。

- 映射表：`docs/help-doc-sync/map.json`（代码路径 → 必须同步的 md 文档）
- 脚本：`scripts/help-doc-sync.mjs`（由 `.husky/pre-commit` 调用）
- 拦截行为：改了受管代码区域（如 `model/analyzeRule/**`）但没同时改对应的 md，commit 会被阻止并给出提示
- 逃生口：`SKIP_DOC_SYNC=1 git commit ...`（仅限确认本次改动不涉及文档内容时使用）
- 扩展映射：编辑 `docs/help-doc-sync/map.json` 即可，无需改脚本

## 核心规则

1. **Check Skills First**: 开始任务前，必须检查是否有匹配的 Skill。
2. **设计先于编码** — 收到功能需求时，先用 brainstorming skill 做需求分析
3. **测试先于实现** — 写代码前先写测试（TDD）
4. **验证先于完成** — 声称完成前必须运行验证命令
5. **发现无关 bug/优化 → follow-up 报告**：任务过程中发现的 bug 或优化点，如果与当前 change 无关，不在本次修，而是作为 follow-up 报告单独提出。
6. **任务有歧义时选最直接的理解**：不要把其他可能的理解也一起做了，只实现最直接的那个理解。
7. **测试文件只在需要时提交**：任务没有明确要求、仓库惯例也不需要时，不提交测试文件。需要提交时，规模参照旁边已有的测试文件。
8. **沟通节奏**：开始执行任务之前，用一句话说明即将要做什么；工作过程中给出简短的进度更新；结尾写一段可以独立看懂的简短总结——发现了什么、做了什么、下一步是什么——让只看到最后一条消息的读者也能了解全貌。
9. **改完代码后走 code-review**：任务完成、代码写完后，自动调用 code-review skill，按 Standards（是否符合项目编码规范）和 Spec（是否符合需求 spec）两个维度审查 diff，两份报告独立输出、不合并不排序。

> **英文对照**：Design First（编码前设计分析）、Test First（TDD）、Verify Before Finish（完成前验证）。

## Skill 的使用

当任务匹配某个 skill 时，使用 `Skill` 工具加载对应 skill 并严格遵循其流程。绝不要用 Read 工具读取 SKILL.md 文件。

当任务明确匹配某个 skill 的应用场景时，应调用该 skill 检查。

## AI 探索项目的方式

1. 先看本文件了解模块结构
2. 定位目标模块，读项目模块的 build.gradle 确认依赖
3. 找该模块的对外接口（api/ 目录或 interface），而不是直接钻进实现
4. 找一个同类型的现有实现作为参考模板，新代码保持风格一致
