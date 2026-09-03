package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.MaterialTheme

/**
 * 页面级通用 Scaffold（migration-review.md §14.2 脚手架项）
 *
 * 对原生 Scaffold 包一层统一配置：
 * - containerColor = Transparent：背景由外层 LegadoBackgroundBox 与顶栏承载，
 *   主题背景图/壁纸才能透出；不透明容器会整块盖住背景
 * - contentWindowInsets = WindowInsets(0, 0, 0, 0)：内容延伸到系统栏区域，
 *   保持沉浸铺满观感
 *
 * ## 适用范围
 *
 * 仅用于**页面级**界面，即宿主为 BaseComposeActivity 或手动调用 setLegadoContent
 * （外层有 LegadoBackgroundBox 兜底背景）。
 * 嵌在 View 体系中的 ComposeView 片段不适用（如 HomepageScreen、
 * BookshelfTagManageScreen、DebugLogScreen）——它们没有背景兜底，
 * 透明容器会露出窗口默认背景。
 *
 * ## 系统栏适配
 *
 * contentWindowInsets = 0 只影响 content 的左右/底部内边距，与顶栏是否延伸到状态栏
 * 无关（顶栏由 TopAppBar 自身的 windowInsets 决定）。适配 insets 的责任在内容侧：
 * - 底部无固定控件：内容容器加 Modifier.navigationBarsPadding()，底部留出导航条高度
 * - 底部有固定按钮/输入框：容器保持铺满，只给该控件加 Modifier.navigationBarsPadding()
 * 判定依据与示例见 compose/migration-review.md §14.3
 *
 * @param modifier Modifier
 * @param topBar 顶栏插槽
 * @param bottomBar 底部栏插槽（可选）
 * @param snackbarHost Snackbar 宿主插槽（可选）
 * @param content 主内容区域
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        content = content
    )
}

// ── 预览（§10.1 强制）────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun AppScaffoldPreview() {
    MaterialTheme {
        AppScaffold(
            topBar = { androidx.compose.material3.Text("AppScaffold") }
        ) { paddingValues ->
            androidx.compose.material3.Text(
                text = "Content",
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}