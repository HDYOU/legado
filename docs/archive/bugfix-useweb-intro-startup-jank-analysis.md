# `<useweb>` 书籍详情页首次进入卡 3 分钟 Bug 分析报告

## 问题描述

用户反馈：同一本书，在姊妹项目 `legado-E` 里点进详情页秒出简介；在本项目 `Legado_Max` 里**首次进入要等大约 3 分钟**才能看到简介，退出重进才能瞬间加载。

复现前提：
- 书源 `BookSource.jsLib` 是 JSON 对象形态，且 value 里至少有一个绝对 URL；
- 书籍 `Book.intro` 以 `<useweb>` 开头，走详情页封面下方简介区的 WebView 内联渲染分支。

一次性事实：`docs/archive/` 目录已经沉淀过一份 `bugfix-backup-image-leak-analysis.md`，本次记录格式与其保持一致，便于后续同类问题归档。

## 调用链定位

入口在 [BookInfoActivity](file:///g:/Project/legado-Max-Suml/Legado_Max/app/src/main/java/io/legado/app/ui/book/info/BookInfoActivity.kt)：

```
ViewModel.bookData.observe(this) { showBook(it) }   ← LiveData 主线程回调
    │
    ▼
showBook(book)
    │
    ▼
showBookIntro(book)
    │
    ├─ intro.startsWith("<useweb>") == true
    ▼
wrapUseWebHtml(rawHtml, bookSource)                 ← 仍在主线程
    │
    ▼
WebJsExtensions.buildUseWebInjection(source)
    │
    ▼
SharedJsScope.getJsLibString(source.jsLib)
    │
    ▼
SharedJsScope.resolveJsLibString(jsLib)
    │
    ├─ aCache.getAsString(md5(url)) == null   ← 首次进入必然未命中
    ▼
runBlocking {                                  ← ⚠ 主线程同步等待网络
    okHttpClient.newCallStrResponse { url(value) }.body
}
```

主线程被 `runBlocking` 挂在网络 IO 上，JS 依赖的 CDN 慢、代理绕、TLS 握手抖动，累积出来的等待时间就是用户感知到的"3 分钟"。下载结束后结果回写 `ACache(cacheDir/shareJs)`，第二次进入时 `aCache.getAsString` 直接命中，`runBlocking` 秒回，所以"退出重进就能出"。

## 根因

**主线程同步阻塞在网络下载**。`SharedJsScope.resolveJsLibString` 为了兼容历史上所有同步调用点（`AnalyzeJs` 在 IO 线程调用它、`WebJsExtensions` 在 Compose Activity 主线程调用它），只在函数内部用了 `runBlocking` 把 suspend 的 `okHttpClient.newCallStrResponse` 拉平为同步返回。这个折中在 IO 线程没问题，在 UI 线程就是 ANR 前奏。

一句话：**UI 线程在门口等外卖，整个 App 的界面都停止响应，等到 CDN 把 jsLib 送完才继续**。

## 与 legado-E 的对比

| 位置 | legado-E | Legado_Max |
|---|---|---|
| `WebJsExtensions.getInjectionString` | 静态字符串 `"try{var cache=…, source=…, java=…;}catch(e){}"` | 同名入口不变，但**新增** `buildUseWebInjection` / `wrapUseWebHtml` |
| `<useweb>` 简介渲染 | 只注入桥接对象，不下载任何外部 JS | 需要拼装 `jsLib`，若包含 URL 则走 `SharedJsScope` 下载 |
| `SharedJsScope` 类 | **不存在**（源码已删除，回归 legado 上游老分支的实现） | 存在，承担书源规则的共享 JS 沙箱 |

`SharedJsScope` 本身不是新增物——上游 legado 一直用它做**书源规则**（`@js:` 前缀）的共享沙箱，跑在 IO 线程没问题。真正的"多出来一步"是本项目把 `jsLib` 也**外溢到了 WebView 的 `<useweb>` 注入路径**：这样书源作者可以直接把工具函数写在 jsLib 里，`<useweb>` 页面的 JS 也能调用。设计动机正确，但没考虑到调用点是主线程。

引入历史（`git log -S` 定位）：

- `878950699` 前后，`WebJsExtensions` 加入 `buildUseWebInjection` / `wrapUseWebHtml`，并在帮助文档 `jsHelp.md` 补了 `<useweb>` 与 jsLib 的说明；
- 同一波改动里 `SharedJsScope` 从"仅供 Rhino 沙箱使用"被推到"UI 渲染路径上也会调用"；
- 主线程阻塞是这次外溢的**副作用**，不是有意设计。

## 影响面

`wrapUseWebHtml` / `buildUseWebInjection` 一共 4 个调用点，全部在主线程：

| 调用点 | 场景 |
|---|---|
| `BookInfoActivity.showBookIntro` `<useweb>` 分支 | 详情页简介 |
| `BookInfoActivity` 内 `CustomWebViewClient` 字段初始化 | 同上，client 挂在 useweb 分支下 |
| `ExploreUseWebView`（Compose）两处 | 发现页 useweb |
| `VideoPlayerActivity.showBookIntro` | 视频页简介 |

任何一个 useweb 页面首次渲染，只要 jsLib 里挂了 URL，都会踩到同一个坑。

## 解决方案

三步叠加，不改动 `resolveJsLibString` 内部 `runBlocking` 的兼容行为（避免波及 IO 线程调用方），只让**主线程不再直接触发它**、并**在它触发之前把缓存预热好**。

### ① 在 `SharedJsScope` 加异步 `prefetch`（commit `1bbc32c13`）

```kotlin
private val prefetchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

fun prefetch(jsLib: String?) {
    // 非 JSON 对象直接 no-op
    // 遍历 value 中的绝对 URL，跳过已命中的，未命中的 launch 到 IO 静默下载
    // 失败只打 AppLog，不抛；不阻塞调用者
}
```

要点：
- 只处理 `isJsonObject()` 形态，纯 JS 字符串形态无需预下载；
- `SupervisorJob` 保证单个 URL 下载失败不影响其他 URL 的 launch；
- 命中 `aCache` 的 URL 直接跳过，不重复触发；
- 完全 no-throw，让 UI 侧调用无感。

### ② 主线程 `<useweb>` 分支切 IO 拼装 HTML（commit `baed0fc71`）

`BookInfoActivity.showBookIntro` 与 `VideoPlayerActivity.showBookIntro` 的 `<useweb>` 分支统一改造：

```kotlin
// 之前
val html = wrapUseWebHtml(rawHtml, source)   // 主线程同步
bindUseWebIntro(html, source)

// 之后
lifecycleScope.launch {
    val html = withContext(IO) { wrapUseWebHtml(rawHtml, source) }
    bindUseWebIntro(html, source)             // 回主线程装配 WebView
}
```

同时把 WebView 的装配逻辑从 `showBookIntro` 中抽出为独立方法 `bindUseWebIntro(html, source)`，KDoc 明确"本方法假定 html 已在 IO 拼装完成，内部不再触碰 `SharedJsScope` 同步接口"，避免后续有人手滑把拼装塞回来。

### ③ 在 `bookSource` 装载点提前 `prefetch`（commit `baed0fc71`）

- `BookInfoViewModel.upBook`：拿到 `bookSource` 后立即 `SharedJsScope.prefetch(it.jsLib)`；
- `ExploreShowViewModel` 三条 bookSource 装载路径同样补上 `prefetch`。

这样当用户从书架点击一本书，`upBook` 一开始异步跑 prefetch；等 `showBookIntro` 真的走到 `<useweb>` 分支时，`aCache` 大概率已经落盘，`resolveJsLibString` 里的 `runBlocking` 变成员级秒回。即使 prefetch 因网络太慢未及完成，`withContext(IO)` 也已经把 `runBlocking` 从主线程挪走，UI 不再冻结。

## 落地结果

- **首次进入 `<useweb>` 详情页**：不再卡 3 分钟；最差情况是 prefetch 未完成 → useweb 拼装等一小段网络时间，但发生在 IO 线程，UI 可继续响应；
- **正常情况**：从书架点击进入详情页的动画 + 主界面绘制这段时间，prefetch 已经完成 → `bindUseWebIntro` 立即拿到 HTML，秒出简介；
- **`legado-E` 侧**：由于本项目保留 `SharedJsScope` 且已消除主线程阻塞，两边行为对齐；不需要把 `SharedJsScope` 删掉。

## 相关提交

| Hash | 说明 |
|---|---|
| `bd7890053` | `docs(书源): 修复 SharedJsScope 中文乱码并补齐 KDoc` — 顺带把文件顶部 KDoc 中"注意：`resolveJsLibString` 为兼容同步调用点在内部使用了 `runBlocking`"这段风险说明沉淀到源码 |
| `1bbc32c13` | `feat(书源): SharedJsScope 新增 jsLib 后台预下载 prefetch` |
| `baed0fc71` | `perf(书源): 消除首次 useweb 简介主线程 runBlocking 卡顿` |

## 可复用的经验

1. **`runBlocking` 在库函数里"兼容同步调用点"是主线程反模式**：只要函数的调用面里存在主线程（Activity / Fragment / Composable），就必须假定它会在主线程被调用；`runBlocking` 里的网络、磁盘、慢计算会立即转化为 UI 卡顿。修法优先级：
   - 短期：所有主线程调用方切 `withContext(IO) { ... }`；
   - 中期：给同步接口旁边加一个 `suspend` 版本，逐步迁移调用方；
   - 长期：库内部改为 `suspend`，删除同步入口。

2. **"退出重进秒开"是首次网络结果被缓存的信号**：看到这种模式，直接排查是否有 `ACache` / `LruCache` / OkHttp Cache 参与；命中路径本身没问题，问题在**首次结果落盘之前主线程被挂住**。

3. **跨项目 diff 定位回归源**：同一开发者的姊妹项目（`legado-E` vs `Legado_Max`）是极好的差分对照物——同名函数在两个项目里的行为差异，往往就是"我们多加了哪一步"的直接答案。本次即通过对比 `WebJsExtensions.getInjectionString` 的有无 `jsLib` 下载环节，快速锁定了 `useweb` 注入路径。

4. **异步预热的正确挂载点是"数据刚拿到"而非"UI 即将渲染"**：`upBook` 拿到 `bookSource` 时即触发 `prefetch`，比在 `showBookIntro` 里做异步等待更早、更充分利用用户从书架点击 → 详情页渲染之间那段动画时间。

## 悬而未决 / 未来清理

- `resolveJsLibString` 内部仍保留 `runBlocking` 以兼容 `AnalyzeJs`（IO 线程）等老调用点。**长期方案**是把它重构为 `suspend`，并同步 `getJsLibString` / `getScope` 也做成 suspend；届时删除 `prefetch`，主线程调用点自然通过挂起函数切 IO。当前未做是考虑到会波及所有书源规则调用方，改动面过大。
- `WebJsExtensions.buildUseWebInjection` / `wrapUseWebHtml` 目前是纯同步签名。若后续有第 5 个调用点，建议**直接把它们也升级为 suspend**，让类型系统强制调用方处理线程，避免又有人顺手放主线程。
