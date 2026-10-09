package com.bushorat.chatapp.helpers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hides the launcher icon by disabling [MainLauncherAlias].
 * Skips redundant PM updates to avoid launcher / activity flashing.
 */
object LauncherIconController {
    private const val TAG = "LauncherIconController"
    private const val LAUNCHER_ALIAS = "MainLauncherAlias"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val operationSeq = AtomicInteger(0)

    @Volatile
    private var desiredVisible: Boolean = true

    @JvmStatic
    fun isLauncherHidden(context: Context): Boolean {
        val app = context.applicationContext
        val pm = app.packageManager
        val alias = aliasComponent(app)
        if (componentState(pm, alias) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            return true
        }
        return pm.getLaunchIntentForPackage(app.packageName) == null
    }

    /** Light touch for periodic enforce — no retries, no new operation generation. */
    @JvmStatic
    fun ensureHidden(context: Context) {
        desiredVisible = false
        val app = context.applicationContext
        if (isLauncherHidden(app)) {
            return
        }
        applyHideNow(app)
    }

    @JvmStatic
    fun ensureVisible(context: Context) {
        desiredVisible = true
        val app = context.applicationContext
        if (!isLauncherHidden(app) && app.packageManager.getLaunchIntentForPackage(app.packageName) != null) {
            return
        }
        applyShowNow(app)
    }

    @JvmStatic
    fun setLauncherVisible(context: Context, visible: Boolean) {
        val app = context.applicationContext
        if (visible == desiredVisible) {
            if (visible && !isLauncherHidden(app)) return
            if (!visible && isLauncherHidden(app)) return
        }
        desiredVisible = visible
        val op = operationSeq.incrementAndGet()
        val wasHidden = isLauncherHidden(app)
        mainHandler.post {
            if (op != operationSeq.get()) return@post
            if (visible) {
                showCompletely(app, op, wasHidden)
            } else {
                hideCompletely(app, op, wasHidden)
            }
        }
    }

    @JvmStatic
    fun applyShowNow(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { applyShowNow(context.applicationContext) }
            return
        }
        val app = context.applicationContext
        if (LauncherProtectionHelper.canUseSystemLauncherHide(app)) {
            LauncherProtectionHelper.showOnLauncherUsingSystemApi(app)
        }
        enableAllLauncherComponents(app)
        enableChooseLoginActivity(app)
    }

    @JvmStatic
    fun applyHideNow(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { applyHideNow(context.applicationContext) }
            return
        }
        val app = context.applicationContext
        if (LauncherProtectionHelper.canUseSystemLauncherHide(app)) {
            LauncherProtectionHelper.hideFromLauncherUsingSystemApi(app)
        }
        disableAllLauncherComponents(app, force = false)
    }

    @JvmStatic
    fun hideLauncherIcon(context: Context) = setLauncherVisible(context, false)

    @JvmStatic
    fun showLauncherIcon(context: Context) = setLauncherVisible(context, true)

    private fun hideCompletely(context: Context, op: Int, wasHidden: Boolean) {
        if (op != operationSeq.get()) return
        applyHideNow(context)
        logHideState(context, "hide")
        if (wasHidden) return
        scheduleHidePass(context, op, 1_000L)
        scheduleHidePass(context, op, 3_000L)
    }

    private fun scheduleHidePass(context: Context, op: Int, delayMs: Long) {
        mainHandler.postDelayed({
            if (op != operationSeq.get() || desiredVisible) return@postDelayed
            if (isLauncherHidden(context)) return@postDelayed
            applyHideNow(context)
        }, delayMs)
    }

    private fun showCompletely(context: Context, op: Int, wasHidden: Boolean) {
        if (op != operationSeq.get()) return
        applyShowNow(context)
        Log.d(
            TAG,
            "Launcher shown; launchIntentAvailable=${context.packageManager.getLaunchIntentForPackage(context.packageName) != null}"
        )
        if (!wasHidden) return
        mainHandler.postDelayed({
            if (op != operationSeq.get() || !desiredVisible) return@postDelayed
            applyShowNow(context)
        }, 800L)
    }

    private fun aliasComponent(context: Context): ComponentName {
        val pkg = context.packageName
        return ComponentName(pkg, "$pkg.$LAUNCHER_ALIAS")
    }

    private fun logHideState(context: Context, label: String) {
        val pm = context.packageManager
        val pkg = context.packageName
        Log.d(
            TAG,
            "$label: launchIntentGone=${pm.getLaunchIntentForPackage(pkg) == null} aliasState=${componentState(pm, aliasComponent(context))}"
        )
    }

    private fun componentState(pm: PackageManager, component: ComponentName): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getComponentEnabledSetting(component)
            } else {
                @Suppress("DEPRECATION")
                pm.getComponentEnabledSetting(component)
            }
        } catch (e: Exception) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
    }

    private fun enableChooseLoginActivity(context: Context) {
        val pm = context.packageManager
        val component = ComponentName(
            context.packageName,
            "com.bushorat.chatapp.activities.ChooseLoginActivity"
        )
        if (componentState(pm, component) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            return
        }
        try {
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not enable ChooseLoginActivity", e)
        }
    }

    private fun disableAllLauncherComponents(context: Context, force: Boolean) {
        applyComponentStates(context, enabled = false, force = force)
    }

    private fun disableAllLauncherComponents(context: Context) {
        disableAllLauncherComponents(context, force = true)
    }

    private fun enableAllLauncherComponents(context: Context) {
        applyComponentStates(context, enabled = true, force = true)
    }

    private fun applyComponentStates(context: Context, enabled: Boolean, force: Boolean) {
        val pm = context.packageManager
        val pkg = context.packageName
        val disabled = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val enabledState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        val flags = PackageManager.DONT_KILL_APP
        val alias = aliasComponent(context)
        val components = linkedSetOf(alias)
        components.addAll(findLauncherComponents(pm, pkg))

        try {
            if (enabled) {
                for (component in components) {
                    val state = componentState(pm, component)
                    if (!force && (state == enabledState || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)) {
                        continue
                    }
                    pm.setComponentEnabledSetting(
                        component,
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                        flags
                    )
                    pm.setComponentEnabledSetting(component, enabledState, flags)
                }
            } else {
                for (component in components) {
                    if (!force && componentState(pm, component) == disabled) {
                        continue
                    }
                    try {
                        pm.setComponentEnabledSetting(component, disabled, flags)
                    } catch (e: Exception) {
                        Log.w(TAG, "Disable failed: ${component.flattenToShortString()}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Component state update failed", e)
        }
    }

    private fun findLauncherComponents(pm: PackageManager, pkg: String): Set<ComponentName> {
        val result = linkedSetOf<ComponentName>()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val flags = PackageManager.MATCH_DEFAULT_ONLY or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.MATCH_DISABLED_COMPONENTS
            } else {
                0
            }
        @Suppress("DEPRECATION")
        val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(flags.toLong())
            )
        } else {
            pm.queryIntentActivities(intent, flags)
        }
        for (info in infos) {
            val ai = info.activityInfo ?: continue
            if (ai.packageName != pkg) continue
            result.add(ComponentName(ai.packageName, ai.name))
        }
        return result
    }
}
