# 协程使用规范（项目级约定）

> 本文件是 Legado_Max 的强制协程约定。项目有一套**自研的链式协程包装**（`io.legado.app.help.coroutine.Coroutine`），
> 与原生 `launch`/`async` 的用法和语义都不同。新人不看本文档极易写错。

## 1. 项目协程 API 总览

所有 `execute` 方法最终都收敛到 `Coroutine.async()`：

```kotlin
// BaseViewModel.kt（execute/executeLazy/submit 仅定义于 BaseViewModel、BaseDialogFragment、BaseService）
fun <T> execute(
    scope: CoroutineScope = viewModelScope,   // 各宿主类默认值不同，见下
    context: CoroutineContext = Dispatchers.IO,   // 业务逻辑执行线程
    start: CoroutineStart = CoroutineStart.DEFAULT,
    executeContext: CoroutineContext = Dispatchers.Main, // 回调执行线程
    semaphore: Semaphore? = null,
    block: suspend CoroutineScope.() -> T
): Coroutine<T>
```

| 宿主类               | `scope` 默认值   | 文件                         |
| -------------------- | ---------------- | ---------------------------- |
| `BaseViewModel`      | `viewModelScope` | `base/BaseViewModel.kt`      |
| `BaseDialogFragment` | `lifecycleScope` | `base/BaseDialogFragment.kt` |
| `BaseService`        | `lifecycleScope` | `base/BaseService.kt`        |

> ⚠️ `BaseActivity` / `BaseFragment` **不提供** `execute`/`executeLazy`/`submit`（实测调用会报 `Unresolved reference 'execute'`）。
> Activity / Fragment 侧的一次性任务：优先下沉到 `ViewModel` 用 `execute`；确需在界面就地做的（如即时读取），
> 用 `lifecycleScope.launch { withContext(IO) { … } }`（参考 `BookSourceEditActivity.alertGroups`）。

返回的 `Coroutine<T>` 是**链式包装类**，不是 `kotlinx.coroutines.Coroutine<T>`（接口），别混淆。支持链式方法：

```kotlin
execute {
    // Dispatchers.IO 上执行，suspend 写法
    repository.loadBook()
}
.timeout(8_000)              // 可选，超时控制
.onStart { /* 开始回调，默认 Main */ }
.onSuccess { value ->        // 默认 Dispatchers.Main 回调
    updateUI(value)
}
.onError { t ->              // 默认 Dispatchers.Main 回调
    toastIt(t)
}
.onFinally { /* 必执行 */ }
.onErrorReturn(fallback)     // 出错时返回兜底值，不再抛给 onError 链
```

每个回调可单独指定 `context` 参数切换线程，例如 `.onSuccess(context = Dispatchers.Default) { ... }`。

## 2. 强制规则

1. **`ViewModel`（继承 `BaseViewModel`）里启动一次性任务，用 `execute`，不要裸 `viewModelScope.launch { }`。**
   理由：项目里 99% 的现有代码是 `execute` 链式风格，错误处理、线程切换、取消语义已统一。混用裸 `launch` 会让错误处理散落各处。
   **Activity / Fragment 没有 `execute` 可用**：一次性任务优先下沉到 `ViewModel` 用 `execute`（UI 持有 `viewModel` 即可 `viewModel.execute { … }`）；确需在界面就地做的用 `lifecycleScope.launch { withContext(IO) { … } }`。
   **例外——Compose 屏幕**：Compose 侧的 ViewModel 不继承 `BaseViewModel`，没有 `execute` 可用；一次性增删改用 `viewModelScope.launch` + `try-catch`（更新 `UiState` + 抛 `Event`），见 `compose/state-events.md` §4.1.1。两者不冲突：`execute` 的 scope 默认就是 `viewModelScope`，取消语义相同，区别只在错误出口（回调链 vs try-catch）。
2. **`execute` 内禁止在 UI 线程做耗时操作。** 默认 `context = Dispatchers.IO`，网络/DB/文件 IO 直接写；CPU 密集（EPUB 解析、大量文本处理）显式传 `context = Dispatchers.Default`。
3. **禁止 `GlobalScope`、禁止 `CoroutineScope(Dispatchers.Main + SupervisorJob())` 手动全局 scope。**
   需要 Application 级后台任务（如缓存清理、定时同步）用 **WorkManager**，不要用长生命周期协程硬扛。
4. **禁止 `runBlocking` 出现在任何非测试代码中。**
5. **`Coroutine<T>` 的坑（源码注释原话）：如果协程太快完成，回调可能不执行。**
   即 `onSuccess` 依赖"任务还没完成时挂上回调"这个时序。不要在 `execute { }` 返回后**另起一个延迟任务**再去挂 `.onSuccess`。回调必须在返回值的同步链上挂完。
   需要"启动后立刻返回、稍后再处理结果"的场景，改用 `executeLazy`（`CoroutineStart.LAZY`）或 `submit`。
6. **取消跟随 Scope 走**：`viewModelScope` 在 ViewModel clear 时取消，`lifecycleScope` 在宿主销毁时取消。不要在 `onDestroy` 里手动 cancel 这些 scope。
   需要手动取消单个任务时，持有 `execute` 返回的 `Coroutine<T>` 实例调用其取消方法。

## 3. Flow 使用位置

- **Repository 层**对外暴露 `Flow`（数据库监听、网络流），ViewModel 层消费。
- ViewModel 中消费 Flow 订阅 UI 状态：`execute { collect }` 或 `lifecycleScope` + `repeatOnLifecycle(STARTED)`（Compose 场景）。
- **禁止**在 Composable 函数体内直接 `collect` 或读数据库；用 `collectAsStateWithLifecycle()` 之类的桥接。
- 一次性事件（导航、toast）不要塞进 `StateFlow`：View 侧走 `LiveEventBus`，Compose 侧走 `Channel<Event>`（缓冲区分档见 compose/state-events.md §4.1，双轨选型裁决见 live-event-bus-rules.md §3 与 README 速查表）。

## 4. Compose 场景红线

Compose 侧的规则以 compose 目录为准，本文件不再重复维护：

- Composable 禁止直接读 DB / 做耗时计算、`remember` / `derivedStateOf` 使用边界 → [compose/performance.md](compose/performance.md)
- 状态收集必须 `collectAsStateWithLifecycle()`、`LazyColumn` key + stable 参数、事件消费绑定生命周期 → [compose/state-events.md](compose/state-events.md)

## 5. 反面示例（看到就改）

```kotlin
// ❌ GlobalScope
GlobalScope.launch { ... }

// ❌ UI 线程读文件
override fun onCreate(...) {
    val txt = File(path).readText()   // 文件在 UI 线程读，ANR 预定
}

// ❌ 裸 launch 且不处理异常
viewModelScope.launch {
    val book = repo.load()
    updateUI(book)   // 抛异常直接崩，无人接
}

// ✅ 项目标准写法
execute {
    repo.load()
}.onSuccess { book -> updateUI(book) }.onError { t -> toastIt(t) }
```

## 6. 测试

- 单测中用 `kotlinx-coroutines-test` 的 `runTest`，配合 `Dispatchers.setMain`。
- `execute` 链在测试中通过 `context = UnconfinedTestDispatcher` 注入，验证 `onSuccess`/`onError` 链路。
