package io.legado.app.lib.theme.view

import android.content.Context
import android.util.AttributeSet
import android.widget.ProgressBar
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.applyTint

/**
 * 主题化进度条。
 *
 * 在初始化时自动应用主题强调色着色（progress / secondaryProgress / indeterminate 三态）。
 */
class ThemeProgressBar(context: Context, attrs: AttributeSet) : ProgressBar(context, attrs) {

    init {
        if (!isInEditMode) {
            applyTint(context.accentColor)
        }
    }
}