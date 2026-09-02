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
 * 页面级通用 Scaffold（theme-styles.md §14.2 脚手架项）
 *
 * 封装统一的 Scaffold 配置：
 * - containerColor = Transparent（背景由内容/顶栏承载）
 * - contentWindowInsets = WindowInsets(0, 0, 0, 0)（让 TopAppBar 延伸到状态栏，
 *   与传统 View 的 TitleBar 行为一致）
 *
 * 所有 Compose 页面级界面应使用此组件替代原生 Scaffold。
 *
 * @param modifier Modifier
 * @param topBar 顶栏插槽
 * @param bottomBar 底部栏插槽（可选）
 * @param content 主内容区域
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = topBar,
        bottomBar = bottomBar,
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