package io.legado.app.lib.theme

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.ColorInt
import io.legado.app.utils.DrawableUtils

/**
 * 视图背景工具。
 *
 * 提供背景过渡动画（TransitionDrawable）与安全背景设置，
 * 用于主题切换时的平滑颜色过渡。
 *
 * @author Karim Abou Zeid (kabouzeid)
 */
@Suppress("unused")
object ViewUtils {

    fun removeOnGlobalLayoutListener(v: View, listener: ViewTreeObserver.OnGlobalLayoutListener) {
        v.viewTreeObserver.removeOnGlobalLayoutListener(listener)
    }

    fun setBackgroundCompat(view: View, drawable: Drawable?) {
        view.background = drawable
    }

    fun setBackgroundTransition(view: View, newDrawable: Drawable): TransitionDrawable {
        val transition = DrawableUtils.createTransitionDrawable(view.background, newDrawable)
        setBackgroundCompat(view, transition)
        return transition
    }

    fun setBackgroundColorTransition(view: View, @ColorInt newColor: Int): TransitionDrawable {
        val oldColor = view.background

        val start = oldColor ?: ColorDrawable(view.solidColor)
        val end = ColorDrawable(newColor)

        val transition = DrawableUtils.createTransitionDrawable(start, end)

        setBackgroundCompat(view, transition)

        return transition
    }
}
