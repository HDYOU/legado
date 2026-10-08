package io.legado.app.model

import androidx.collection.LruCache
import com.google.gson.reflect.TypeToken
import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.ACache
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonObject
import kotlinx.coroutines.runBlocking
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import splitties.init.appCtx
import java.io.File
import java.lang.ref.WeakReference
import kotlin.coroutines.CoroutineContext

/**
 * 书源共享 JS 作用域管理器。
 *
 * 书源 `BookSource.jsLib` 允许作者声明一段跨规则复用的 JS 代码：
 * 可以是普通 JS 字符串，也可以是 JSON 对象 `{ "名字": "内联JS 或 远程URL", ... }`。
 * 本对象承担 4 件事：
 * 1. 把 jsLib 中指向 URL 的片段下载并落到 [ACache] 磁盘缓存，避免每次执行都联网；
 * 2. 把所有片段拼装成一份可直接 eval / 内联注入的 JS 字符串（[getJsLibString]）；
 * 3. 给 Rhino 引擎构建一份共享的 [Scriptable] 运行时作用域（[getScope]），
 *    并用 [LruCache] + [WeakReference] 缓存 16 项，减少重复初始化开销；
 * 4. jsLib 被更新或删除时同步清掉内存/磁盘缓存（[remove]）。
 *
 * 典型调用点：
 * - AnalyzeRule / AnalyzeJs 执行书源规则时通过 [getScope] 取共享作用域；
 * - [io.legado.app.help.webView.WebJsExtensions] 通过 [getJsLibString] 把 jsLib 内联注入 WebView。
 *
 * 注意：[resolveJsLibString] 为兼容同步调用点在内部使用了 [runBlocking] 做 URL 下载，
 * 若在 UI 线程调用会阻塞渲染，调用方应尽量放到 IO 线程预热或改造为 suspend 版本。
 */
object SharedJsScope {

    /** jsLib 下载后的磁盘缓存目录（APP cacheDir/shareJs），随系统清理 */
    private val cacheFolder = File(appCtx.cacheDir, "shareJs")
    private val aCache = ACache.get(cacheFolder)

    /**
     * jsLib → Rhino 作用域的内存缓存。
     * key 用 `MD5(jsLib)` 做指纹；value 用 [WeakReference] 包住，
     * 防止外部不再引用时仍被 object 生命周期锁住，允许 GC 回收。
     */
    private val scopeMap = LruCache<String, WeakReference<Scriptable>>(16)

    /**
     * jsLib 解析核心：下载 URL 片段并按顺序拼装成一段可执行的 JS 字符串。
     *
     * 处理策略：
     * - `null` 或空白 → 返回 `null`；
     * - 不是 JSON 对象（普通 JS 字符串）→ 原样返回；
     * - 是 JSON 对象 → 遍历所有 value：
     *   - value 是绝对 URL：先查 [aCache]；命中直接用，未命中通过 [runBlocking] 同步下载并回写缓存；
     *     下载失败抛 [NoStackTraceException]；
     *   - value 是内联 JS：直接使用；
     *   - 非空片段按顺序用 `\n` 拼接。
     *
     * 被 [getScope] 和 [getJsLibString] 共同复用，属于"下载 + 拼装"的唯一入口。
     */
    private fun resolveJsLibString(jsLib: String?): String? {
        if (jsLib.isNullOrBlank()) {
            return null
        }
        return if (jsLib.isJsonObject()) {
            val jsMap: Map<String, String> = GSON.fromJson(
                jsLib,
                TypeToken.getParameterized(
                    Map::class.java,
                    String::class.java,
                    String::class.java
                ).type
            )
            buildString {
                jsMap.values.forEach { value ->
                    val js = when {
                        value.isAbsUrl() -> {
                            val fileName = MD5Utils.md5Encode(value)
                            var cacheJs = aCache.getAsString(fileName)
                            if (cacheJs == null) {
                                cacheJs = runBlocking {
                                    okHttpClient.newCallStrResponse {
                                        url(value)
                                    }.body
                                }
                                if (cacheJs != null) {
                                    aCache.put(fileName, cacheJs)
                                } else {
                                    throw NoStackTraceException("下载jsLib-${value}失败")
                                }
                            }
                            cacheJs
                        }

                        else -> value
                    }
                    if (!js.isNullOrBlank()) {
                        if (isNotEmpty()) append('\n')
                        append(js)
                    }
                }
            }.takeIf { it.isNotBlank() }
        } else {
            jsLib
        }
    }

    /**
     * 取或构建基于 jsLib 的 Rhino 运行时作用域。
     *
     * 相同 jsLib 用 `MD5(jsLib)` 作 key 走 [scopeMap]；命中直接返回。
     * 未命中时：
     * 1. 通过 [RhinoScriptEngine.getRuntimeScope] 新建一份空 scope；
     * 2. 用 [resolveJsLibString] 拿到拼装后的 JS 文本并 `eval` 进 scope；
     * 3. 若 scope 是 [ScriptableObject]，调用 `preventExtensions()` 锁死全局变量表；
     * 4. 用 [WeakReference] 包好后放回 [scopeMap]。
     *
     * @param jsLib 书源 jsLib 字段原文（普通 JS 字符串 或 JSON 对象），空则返回 `null`
     * @param coroutineContext 传给 [RhinoScriptEngine.eval] 的协程上下文，用于取消传播
     */
    fun getScope(jsLib: String?, coroutineContext: CoroutineContext?): Scriptable? {
        if (jsLib.isNullOrBlank()) {
            return null
        }
        val key = MD5Utils.md5Encode(jsLib)
        var scope = scopeMap[key]?.get()
        if (scope == null) {
            scope = RhinoScriptEngine.run {
                getRuntimeScope(ScriptBindings())
            }
            resolveJsLibString(jsLib)?.let {
                RhinoScriptEngine.eval(it, scope, coroutineContext)
            }
            if (scope is ScriptableObject) {
                /**
                 * 锁死全局变量表：函数内未使用 `var` 声明的赋值会被直接拒绝，
                 * 后续读取该"隐式全局变量"时报"变量未定义"，避免跨规则相互污染。
                 */
                scope.preventExtensions()
            }
            scopeMap.put(key, WeakReference(scope))
        }
        return scope
    }

    /**
     * 只做"下载 + 拼装"，返回可直接塞给 WebView / eval 的 JS 字符串，
     * 不构建 Rhino 作用域，也不进 [scopeMap] 缓存。
     *
     * 主要给 [io.legado.app.help.webView.WebJsExtensions.buildUseWebInjection]
     * 这类只需要 JS 文本、不需要 Scriptable 的场景使用。
     *
     * 同样存在 URL 未命中缓存时通过 [runBlocking] 同步下载的行为，
     * 主线程直接调用会阻塞渲染，调用方需注意切线程。
     */
    fun getJsLibString(jsLib: String?): String? {
        return resolveJsLibString(jsLib)
    }

    /**
     * 让 jsLib 相关缓存失效：书源被编辑或删除时调用。
     *
     * - 若 jsLib 是 JSON 对象，遍历其中所有 URL 型 value，删除它们在 [aCache] 中的磁盘缓存；
     * - 不论哪种形式，都从 [scopeMap] 中移除对应的内存项。
     *
     * 下一次 [getScope] / [getJsLibString] 会重新走下载与 eval 流程。
     */
    fun remove(jsLib: String?) {
        if (jsLib.isNullOrBlank()) {
            return
        }
        if (jsLib.isJsonObject()) {
            val jsMap: Map<String, String> = GSON.fromJson(
                jsLib,
                TypeToken.getParameterized(
                    Map::class.java,
                    String::class.java,
                    String::class.java
                ).type
            )
            jsMap.values.forEach { value ->
                if (value.isAbsUrl()) {
                    val fileName = MD5Utils.md5Encode(value)
                    aCache.remove(fileName)
                }
            }
        }
        val key = MD5Utils.md5Encode(jsLib)
        scopeMap.remove(key)
    }

}
