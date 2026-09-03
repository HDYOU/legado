@file:Suppress("unused")

package io.legado.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import io.legado.app.BuildConfig
import io.legado.app.databinding.ViewToastBinding
import io.legado.app.data.repository.debug.DebugEventCenter
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.model.debug.DebugCategory
import io.legado.app.model.debug.DebugEvent
import io.legado.app.model.debug.DebugLevel
import io.legado.app.model.debug.ToastContext
import io.legado.app.help.LifecycleHelp
import io.legado.app.utils.runOnUI
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import splitties.systemservices.layoutInflater

private var toast: Toast? = null

private var toastLegacy: Toast? = null

private val toastHandler by lazy { buildMainHandler() }

/** 兜底清理延迟：Toast.LENGTH_SHORT 实际展示约 2s，留余量后清空静态引用。 */
private const val SHORT_TOAST_CLEAN_DELAY = 3_000L

/** 兜底清理延迟：Toast.LENGTH_LONG 实际展示约 3.5s，留余量后清空静态引用。 */
private const val LONG_TOAST_CLEAN_DELAY = 5_000L

/**
 * 在 Toast 显示结束（view 被移除）时回调，用于释放静态引用，避免 Toast 被静态持有导致泄漏。
 * [onDismiss] 中需自行判断当前引用是否仍指向被 dismiss 的实例，防止误清新创建的 Toast。
 */
private fun Toast?.releaseWhenDismissed(onDismiss: (Toast?) -> Unit) {
    this?.view?.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) {
            if (this@releaseWhenDismissed?.view === v) {
                onDismiss(this@releaseWhenDismissed)
            }
        }
    })
}

/**
 * 创建并展示自定义样式的 Toast，旧的实例先 cancel，再让静态引用指向新实例。
 *
 * 关键约束：view 必须用 [applicationContext] 的 LayoutInflater 创建，严禁使用
 * Activity/Fragment 的 inflater。否则 view.mContext 会直接引用调用方界面，而 Toast 又
 * 被静态字段持有——只要界面在 Toast 消失前销毁，整条 Activity 链就会被拖住（LeakCanary
 * 曾捕获：static toast -> Toast.mNextView -> View.mContext -> 已销毁的 ThemeManageActivity，
 * 一次就挂住 22.6MB）。
 *
 * 静态引用的清理分两路兜底：
 * 1. view 正常展示结束被移除时（detach）回调清空；
 * 2. 若 Toast 在 attach 之前就被 cancel（如快速连发），view 永远不会 detach、回调不会
 *    触发，静态字段会一直挂着旧 Toast。因此再按显示时长延时兜底清空一次，避免长期持有。
 */
@SuppressLint("InflateParams")
@Suppress("DEPRECATION")
private fun Context.showCustomToast(
    message: CharSequence?,
    duration: Int,
    afterShow: () -> Unit
) {
    runOnUI {
        kotlin.runCatching {
            toast?.cancel()
            val newToast = Toast(applicationContext)
            val isLight = ColorUtils.isColorLight(bottomBackground)
            ViewToastBinding.inflate(applicationContext.layoutInflater).run {
                newToast.view = root
                cvToast.setCardBackgroundColor(bottomBackground)
                tvText.setTextColor(getPrimaryTextColor(isLight))
                tvText.text = message
            }
            newToast.duration = duration
            newToast.releaseWhenDismissed { dismissed ->
                if (toast === dismissed) toast = null
            }
            toast = newToast
            newToast.show()
            val cleanDelay = if (duration == Toast.LENGTH_LONG) {
                LONG_TOAST_CLEAN_DELAY
            } else {
                SHORT_TOAST_CLEAN_DELAY
            }
            toastHandler.postDelayed({
                if (toast === newToast) toast = null
            }, cleanDelay)
            afterShow()
        }
    }
}

fun Context.toastOnUi(message: Int, duration: Int = Toast.LENGTH_SHORT) {
    toastOnUi(getString(message), duration)
}

fun Context.toastOnUi(message: CharSequence?, duration: Int = Toast.LENGTH_SHORT) {
    showCustomToast(message, duration) {
        // 记录Toast到调试日志
        recordToast(message, duration)
    }
}

fun Context.toastOnUiLegacy(message: CharSequence) {
    runOnUI {
        kotlin.runCatching {
            if (toastLegacy == null || BuildConfig.DEBUG || AppConfig.recordLog) {
                toastLegacy = Toast.makeText(this.applicationContext, message, Toast.LENGTH_SHORT)
            } else {
                toastLegacy?.setText(message)
                toastLegacy?.duration = Toast.LENGTH_SHORT
            }
            toastLegacy?.releaseWhenDismissed { dismissed ->
                if (toastLegacy === dismissed) toastLegacy = null
            }
            toastLegacy?.show()
            
            // 记录Toast到调试日志
            recordToast(message, Toast.LENGTH_SHORT)
        }
    }
}

fun Context.longToastOnUi(message: Int) {
    toastOnUi(message, Toast.LENGTH_LONG)
}

fun Context.longToastOnUi(message: CharSequence?) {
    toastOnUi(message, Toast.LENGTH_LONG)
}

fun Context.longToastOnUiLegacy(message: CharSequence) {
    runOnUI {
        kotlin.runCatching {
            if (toastLegacy == null || BuildConfig.DEBUG || AppConfig.recordLog) {
                toastLegacy = Toast.makeText(this.applicationContext, message, Toast.LENGTH_LONG)
            } else {
                toastLegacy?.setText(message)
                toastLegacy?.duration = Toast.LENGTH_LONG
            }
            toastLegacy?.releaseWhenDismissed { dismissed ->
                if (toastLegacy === dismissed) toastLegacy = null
            }
            toastLegacy?.show()
            
            // 记录Toast到调试日志
            recordToast(message, Toast.LENGTH_LONG)
        }
    }
}

fun Fragment.toastOnUi(message: Int) = requireActivity().toastOnUi(message)

fun Fragment.toastOnUi(message: CharSequence) = requireActivity().toastOnUi(message)

fun Fragment.longToast(message: Int) = requireContext().longToastOnUi(message)

fun Fragment.longToast(message: CharSequence) = requireContext().longToastOnUi(message)

/**
 * 记录Toast消息到调试日志
 */
@OptIn(DelicateCoroutinesApi::class)
private fun recordToast(message: CharSequence?, duration: Int, context: ToastContext = ToastContext()) {
    if (message.isNullOrBlank()) return
    
    val durationText = if (duration == Toast.LENGTH_LONG) "长" else "短"
    
    val activityName = context.activityName ?: LifecycleHelp.getCurrentActivityName()
    val mergedContext = ToastContext(
        activityName = activityName,
        sourceName = context.sourceName,
        sourceType = context.sourceType,
        ruleType = context.ruleType,
        ruleLine = context.ruleLine
    )
    
    GlobalScope.launch(Dispatchers.Default) {
        DebugEventCenter.emit(
            DebugEvent(
                level = DebugLevel.INFO,
                category = DebugCategory.TOAST,
                message = "[${durationText}Toast] $message",
                detail = message.toString(),
                sourceName = mergedContext.sourceName,
                tags = mergedContext.toTagsMap()
            )
        )
    }
}

fun Context.toastOnUi(message: CharSequence?, context: ToastContext, duration: Int = Toast.LENGTH_SHORT) {
    showCustomToast(message, duration) {
        recordToast(message, duration, context)
    }
}

fun Context.longToastOnUi(message: CharSequence?, context: ToastContext) {
    toastOnUi(message, context, Toast.LENGTH_LONG)
}

fun Fragment.toastOnUi(message: CharSequence, context: ToastContext) = requireActivity().toastOnUi(message, context)

fun Fragment.longToast(message: CharSequence, context: ToastContext) = requireContext().longToastOnUi(message, context)
