package io.legado.app.lib.theme

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.AttrRes

/**
 * 主题属性解析工具。
 *
 * 从主题中解析颜色、尺寸、 drawable 等属性值，提供带降级默认值的安全读取。
 * 所有方法均带 @JvmOverloads 以便 Java 侧调用。
 *
 * @author Aidan Follestad (afollestad)
 */
object ThemeUtils {

    @JvmOverloads
    fun resolveColor(context: Context, @AttrRes attr: Int, fallback: Int = 0): Int {
        val a = context.theme.obtainStyledAttributes(intArrayOf(attr))
        return try {
            a.getColor(0, fallback)
        } catch (e: Exception) {
            fallback
        } finally {
            a.recycle()
        }
    }

    @JvmOverloads
    fun resolveFloat(context: Context, @AttrRes attr: Int, fallback: Float = 0.0f): Float {
        val a = context.theme.obtainStyledAttributes(intArrayOf(attr))
        return try {
            a.getFloat(0, fallback)
        } catch (e: Exception) {
            fallback
        } finally {
            a.recycle()
        }
    }

    fun resolveDrawable(context: Context, @AttrRes attr: Int): Drawable? {
        val a = context.theme.obtainStyledAttributes(intArrayOf(attr))
        return try {
            a.getDrawable(0)
        } finally {
            a.recycle()
        }
    }
}