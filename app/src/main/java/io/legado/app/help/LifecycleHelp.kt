package io.legado.app.help

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.legado.app.base.BaseService
import io.legado.app.lib.theme.EInkRender
import io.legado.app.ui.debuglog.DebugFloatingBallManager
import io.legado.app.utils.LogUtils
import java.lang.ref.WeakReference

/**
 * Activity管理器,管理项目中Activity的状态
 */
@Suppress("unused")
object LifecycleHelp : Application.ActivityLifecycleCallbacks {

    private const val TAG = "LifecycleHelp"

    private val activities: MutableList<WeakReference<Activity>> = arrayListOf()
    private val services: MutableList<WeakReference<BaseService>> = arrayListOf()
    private var appFinishedListener: (() -> Unit)? = null
    private var currentActivityRef: WeakReference<Activity>? = null

    /**
     * 处于 started 状态的 Activity 数量。
     *
     * 应用内 Activity 互相切换时，新 Activity 的 onStart 早于上一个 Activity 的 onStop，
     * 计数不会归零；只有当整个 App 退到后台时才会归零。
     * 因此「计数由 0 变 1」即可判定为 App 从外部进入前台。
     */
    private var startedActivityCount = 0

    /**
     * 最近一次进入前台是否来自 App 外部（冷启动、后台切回、外部应用跳入）。
     * 由 [consumeEnteredFromExternal] 消费式读取，保证一次进入只被消费一次。
     */
    private var enteredFromExternal = false

    fun activitySize(): Int = activities.size

    /**
     * 标记本次进入前台来自 App 外部。
     *
     * singleTask 的 Activity 在 App 已处于前台时收到外部 intent 只回调 onNewIntent，
     * 不走 onStart，[startedActivityCount] 不会变化，需要由调用方显式标记。
     */
    @Synchronized
    fun markEnteredFromExternal() {
        enteredFromExternal = true
    }

    /**
     * 消费「最近一次进入前台是否来自 App 外部」的标记，读取后清空。
     *
     * @return true 表示本次进入是从 App 外部（冷启动、后台切回、外部应用跳入）而来
     */
    @Synchronized
    fun consumeEnteredFromExternal(): Boolean {
        val value = enteredFromExternal
        enteredFromExternal = false
        return value
    }

    fun getCurrentActivity(): Activity? = currentActivityRef?.get()

    fun getCurrentActivityName(): String? = getCurrentActivity()?.javaClass?.simpleName

    /**
     * 判断指定Activity是否存在
     */
    fun isExistActivity(activityClass: Class<*>): Boolean {
        activities.forEach { item ->
            if (item.get()?.javaClass == activityClass) {
                return true
            }
        }
        return false
    }

    /**
     * 关闭指定 activity(class)
     */
    fun finishActivity(vararg activityClasses: Class<*>) {
        val waitFinish = ArrayList<WeakReference<Activity>>()
        for (temp in activities) {
            for (activityClass in activityClasses) {
                if (temp.get()?.javaClass == activityClass) {
                    waitFinish.add(temp)
                    break
                }
            }
        }
        waitFinish.forEach {
            it.get()?.finish()
        }
    }

    fun setOnAppFinishedListener(appFinishedListener: (() -> Unit)) {
        this.appFinishedListener = appFinishedListener
    }

    override fun onActivityPaused(activity: Activity) {
        LogUtils.d(TAG, "${activity::class.simpleName} onPause")
    }

    override fun onActivityResumed(activity: Activity) {
        LogUtils.d(TAG, "${activity::class.simpleName} onResume")
        currentActivityRef = WeakReference(activity)
    }

    @Synchronized
    override fun onActivityStarted(activity: Activity) {
        LogUtils.d(TAG, "${activity::class.simpleName} onStart")
        if (startedActivityCount == 0) {
            enteredFromExternal = true
        }
        startedActivityCount++
        // 墨水屏渲染：Activity 窗口统一按灰阶合成（见 EInkRender.applyRootLayer）。
        // 挂在这里而不是某个基类上：Compose 页面走 BaseComposeActivity、View 页面走
        // BaseActivity，两者没有公共基类。内容根是懒安装的，onCreate 回调时可能还不存在，
        // onStart 时一定已建好；同类型 + 同一 Paint 的重复调用不会重建图层。
        if (EInkRender.supportsRootLayer(activity)) {
            EInkRender.applyRootLayer(activity.findViewById(android.R.id.content))
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        LogUtils.d(TAG, "${activity::class.simpleName} onDestroy")
        for (temp in activities) {
            if (temp.get() != null && temp.get() === activity) {
                activities.remove(temp)
                if (services.isEmpty() && activities.isEmpty()) {
                    onAppFinished()
                }
                break
            }
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        LogUtils.d(TAG, "${activity::class.simpleName} onSaveInstanceState")
    }

    @Synchronized
    override fun onActivityStopped(activity: Activity) {
        LogUtils.d(TAG, "${activity::class.simpleName} onStop")
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        LogUtils.d(TAG, "${activity::class.simpleName} onCreate")
        activities.add(WeakReference(activity))
    }

    @Synchronized
    fun onServiceCreate(service: BaseService) {
        LogUtils.d(TAG, "${service::class.simpleName} onCreate")
        services.add(WeakReference(service))
    }

    @Synchronized
    fun onServiceDestroy(service: BaseService) {
        LogUtils.d(TAG, "${service::class.simpleName} onDestroy")
        for (temp in services) {
            if (temp.get() != null && temp.get() === service) {
                services.remove(temp)
                if (services.isEmpty() && activities.isEmpty()) {
                    onAppFinished()
                }
                break
            }
        }
    }

    private fun onAppFinished() {
        DebugFloatingBallManager.onAppFinished()
        appFinishedListener?.invoke()
    }
}
