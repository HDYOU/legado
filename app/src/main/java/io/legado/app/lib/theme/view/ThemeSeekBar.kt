package io.legado.app.lib.theme.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSeekBar
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.applyTint

/**
 * 主题化滑动条。
 *
 * 在初始化时自动应用主题强调色着色（thumb / progress 双状态）。
 *
 * @author Aidan Follestad (afollestad)
 */
class ThemeSeekBar(context: Context, attrs: AttributeSet) : AppCompatSeekBar(context, attrs) {

    init {
        if (!isInEditMode) {
            applyTint(context.accentColor)
        }
    }
}
