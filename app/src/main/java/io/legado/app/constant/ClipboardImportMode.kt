package io.legado.app.constant

/**
 * 剪贴板导入口令的处理模式，对应设置项 [PreferKey.clipboardImportMode]。
 *
 * 用于决定「从 App 外部进入主界面且剪贴板中存在 #L: 口令」时的行为：
 * 询问、自动导入，还是完全不读剪贴板。
 */
object ClipboardImportMode {

    /** 每次都弹窗询问，可勾选「记住我的选择」固化为 [ALWAYS] 或 [NEVER] */
    const val ASK = "ask"

    /** 始终自动导入，导入时 Toast 提示 */
    const val ALWAYS = "always"

    /** 从不读取剪贴板 */
    const val NEVER = "never"
}
