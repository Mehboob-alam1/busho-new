package com.busho.app.helpers

import android.app.ActivityManager
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
 * Removes the app from the launcher by disabling MAIN/LAUNCHER aliases.
 * On OnePlus/OxygenOS, also blank-swaps the icon then force-restarts the home
 * launcher so ghost tiles drop without rebooting the phone.
 *
 * Filter: adb logcat -s HIDE_DIAG:I
 */
object LauncherIconController {
    private const val TAG = "LauncherIconController"
    const val DIAG = "HIDE_DIAG"

    private const val MAIN_LAUNCHER = "com.busho.app.MainLauncherActivity"
    private const val VISIBLE_ALIAS = "com.busho.app.VisibleLauncherAlias"
    private const val BLANK_ALIAS = "com.busho.app.BlankLauncherAlias"

    private const val PREFS = "launcher_icon_controller"
    private const val KEY_DESIRED_VISIBLE = "desired_visible"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val operationSeq = AtomicInteger(0)

    @Volatile
    private var desiredVisible: Boolean = true

    private fun stateName(state: Int): String = when (state) {
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> "DEFAULT"
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> "ENABLED"
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> "DISABLED"
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> "DISABLED_USER"
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> "DISABLED_UNTIL_USED"
        else -> "UNKNOWN($state)"
    }

    private fun callerHint(): String {
        val stack = Throwable().stackTrace
        for (el in stack) {
            val cn = el.className
            if (cn.contains("LauncherIconController")) continue
            if (cn.startsWith("java.") || cn.startsWith("android.") || cn.startsWith("kotlin.")) continue
            return "${el.className.substringAfterLast('.')}.${el.methodName}:${el.lineNumber}"
        }
        return "unknown"
    }

    private fun persistDesired(context: Context, visible: Boolean) {
        desiredVisible = visible
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DESIRED_VISIBLE, visible)
            .commit()
    }

    private fun loadDesired(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DESIRED_VISIBLE, true)
    }

    private fun isOxygenFamily(): Boolean {
        val brand = Build.BRAND.orEmpty().lowercase()
        val mfr = Build.MANUFACTURER.orEmpty().lowercase()
        return listOf("oneplus", "oppo", "realme", "oplus").any {
            brand.contains(it) || mfr.contains(it)
        }
    }

    @JvmStatic
    fun dumpHideDiagnostics(context: Context, reason: String) {
        val app = context.applicationContext
        val pm = app.packageManager
        val pkg = app.packageName
        val main = ComponentName(pkg, MAIN_LAUNCHER)
        val visible = ComponentName(pkg, VISIBLE_ALIAS)
        val blank = ComponentName(pkg, BLANK_ALIAS)
        val launchIntent = pm.getLaunchIntentForPackage(pkg)
        val launchers = findLauncherComponents(pm, pkg, includeDisabled = true)
        val enabledLaunchers = findLauncherComponents(pm, pkg, includeDisabled = false)

        val sb = StringBuilder()
        sb.appendLine("======== HIDE DIAG [$reason] ========")
        sb.appendLine("pkg=$pkg sdk=${Build.VERSION.SDK_INT} brand=${Build.BRAND} model=${Build.MODEL}")
        sb.appendLine("desiredVisible=$desiredVisible persistedDesired=${loadDesired(app)} opSeq=${operationSeq.get()}")
        sb.appendLine("isLauncherHidden()=${isLauncherHidden(app)} oxygenFamily=${isOxygenFamily()}")
        sb.appendLine("main=$MAIN_LAUNCHER state=${stateName(componentState(pm, main))}")
        sb.appendLine("visibleAlias state=${stateName(componentState(pm, visible))}")
        sb.appendLine("blankAlias state=${stateName(componentState(pm, blank))}")
        sb.appendLine("getLaunchIntentForPackage=${launchIntent?.component?.flattenToShortString() ?: "NULL"}")
        sb.appendLine("applicationEnabledSetting=${stateName(applicationEnabledState(pm, pkg))}")
        sb.appendLine("deviceOwner=${LauncherProtectionHelper.isDeviceOwner(app)} adminActive=${LauncherProtectionHelper.isAdminActive(app)}")
        sb.appendLine("systemHidden=${LauncherProtectionHelper.isApplicationHiddenBySystem(app)}")
        sb.appendLine("defaultHome=${resolveDefaultHomePackage(app) ?: "null"}")
        sb.appendLine("launcherComponents(all+disabled)=${launchers.size}")
        for (c in launchers) {
            sb.appendLine("  ALL  ${c.flattenToShortString()} state=${stateName(componentState(pm, c))}")
        }
        sb.appendLine("launcherComponents(ENABLED only)=${enabledLaunchers.size}")
        for (c in enabledLaunchers) {
            sb.appendLine("  LIVE ${c.flattenToShortString()} state=${stateName(componentState(pm, c))}")
        }
        when {
            !desiredVisible && enabledLaunchers.isNotEmpty() ->
                sb.appendLine(">>> PROBLEM: hide desired but ${enabledLaunchers.size} LIVE launcher(s)")
            !desiredVisible && launchIntent != null ->
                sb.appendLine(">>> PROBLEM: hide desired but launch intent still set → ${launchIntent.component}")
            !desiredVisible && isLauncherHidden(app) ->
                sb.appendLine(">>> OK: no resolvable launcher — ghost tile needs OEM launcher restart (we force-kill home)")
            desiredVisible ->
                sb.appendLine(">>> NOTE: desiredVisible=true — SHOW path")
            else ->
                sb.appendLine(">>> PROBLEM: hide desired but isLauncherHidden=false")
        }
        sb.appendLine("======== END HIDE DIAG ========")
        Log.i(DIAG, sb.toString())
        Log.d(TAG, sb.toString())
    }

    @JvmStatic
    fun isLauncherHidden(context: Context): Boolean {
        val app = context.applicationContext
        val pm = app.packageManager
        val appState = applicationEnabledState(pm, app.packageName)
        if (appState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            || appState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        ) {
            return true
        }
        val noLaunchIntent = pm.getLaunchIntentForPackage(app.packageName) == null
        val enabledCount = findLauncherComponents(pm, app.packageName, includeDisabled = false).size
        return noLaunchIntent && enabledCount == 0
    }

    @JvmStatic
    fun ensureHidden(context: Context) {
        Log.i(DIAG, "ensureHidden() caller=${callerHint()} desiredVisible was=$desiredVisible")
        persistDesired(context, false)
        applyHideNow(context.applicationContext)
        dumpHideDiagnostics(context, "after-ensureHidden")
    }

    @JvmStatic
    fun ensureVisible(context: Context) {
        Log.i(DIAG, "ensureVisible() caller=${callerHint()}")
        if (!loadDesired(context) || !desiredVisible) {
            Log.w(DIAG, "ensureVisible BLOCKED — desiredVisible=false (hide wins)")
            dumpHideDiagnostics(context, "ensureVisible-blocked")
            return
        }
        persistDesired(context, true)
        val app = context.applicationContext
        if (!isLauncherHidden(app) && app.packageManager.getLaunchIntentForPackage(app.packageName) != null) {
            Log.i(DIAG, "ensureVisible: already visible, skip")
            return
        }
        applyShowNow(app)
        dumpHideDiagnostics(context, "after-ensureVisible")
    }

    @JvmStatic
    fun setLauncherVisible(context: Context, visible: Boolean) {
        val app = context.applicationContext
        Log.i(
            DIAG,
            "setLauncherVisible(visible=$visible) caller=${callerHint()} wasDesired=$desiredVisible wasHidden=${isLauncherHidden(app)}"
        )
        persistDesired(app, visible)
        val op = operationSeq.incrementAndGet()
        val wasHidden = isLauncherHidden(app)
        mainHandler.post {
            if (op != operationSeq.get()) {
                Log.w(DIAG, "setLauncherVisible op=$op superseded by ${operationSeq.get()}")
                return@post
            }
            if (visible && !loadDesired(app)) {
                Log.w(DIAG, "setLauncherVisible SHOW aborted — hide desired")
                hideCompletely(app, op)
                return@post
            }
            if (visible) {
                showCompletely(app, op, wasHidden)
            } else {
                hideCompletely(app, op)
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
        if (!desiredVisible || !loadDesired(app)) {
            Log.w(DIAG, "applyShowNow BLOCKED — desiredVisible=false caller=${callerHint()}")
            return
        }
        Log.i(DIAG, "applyShowNow caller=${callerHint()}")
        if (LauncherProtectionHelper.canUseSystemLauncherHide(app)) {
            LauncherProtectionHelper.showOnLauncherUsingSystemApi(app)
        }
        // Ensure package itself is enabled (in case a prior hide left it disabled).
        ensureApplicationEnabled(app)
        setComponent(app, BLANK_ALIAS, enabled = false, killApp = false)
        setComponent(app, VISIBLE_ALIAS, enabled = true, killApp = false)
        setComponent(app, MAIN_LAUNCHER, enabled = true, killApp = false)
        enableChooseLoginActivity(app)
        forceRestartHomeLauncher(app)
        dumpHideDiagnostics(app, "after-applyShowNow")
    }

    @JvmStatic
    fun applyHideNow(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { applyHideNow(context.applicationContext) }
            return
        }
        val app = context.applicationContext
        Log.i(DIAG, "applyHideNow(soft) caller=${callerHint()} oxygen=${isOxygenFamily()}")
        if (LauncherProtectionHelper.canUseSystemLauncherHide(app)) {
            val ok = LauncherProtectionHelper.hideFromLauncherUsingSystemApi(app)
            Log.i(DIAG, "hideFromLauncherUsingSystemApi result=$ok")
        }
        // Never enable blank during hide — that left LIVE launchers on OnePlus.
        disableAllLauncherComponents(app, killApp = false)
        // Same as hideunhide sample: disable the whole application (DONT_KILL_APP).
        disableApplicationLikeSample(app)
        removeFromRecents(app)
        forceRestartHomeLauncher(app)
        dumpHideDiagnostics(app, "after-applyHideNow-soft")
    }

    @JvmStatic
    fun applyHideHard(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { applyHideHard(context.applicationContext) }
            return
        }
        val app = context.applicationContext
        Log.i(DIAG, "applyHideHard() caller=${callerHint()}")
        if (LauncherProtectionHelper.canUseSystemLauncherHide(app)) {
            val ok = LauncherProtectionHelper.hideFromLauncherUsingSystemApi(app)
            Log.i(DIAG, "hideFromLauncherUsingSystemApi(hard) result=$ok")
        }
        // Keep DONT_KILL_APP — killing the process after setApplicationEnabledSetting(DISABLED)
        // bricks restore until Settings/adb enables the package.
        disableAllLauncherComponents(app, killApp = false)
        disableApplicationLikeSample(app)
        removeFromRecents(app)
        forceRestartHomeLauncher(app)
        dumpHideDiagnostics(app, "after-applyHideHard")
    }

    @JvmStatic
    fun hideLauncherIcon(context: Context) = setLauncherVisible(context, false)

    @JvmStatic
    fun showLauncherIcon(context: Context) = setLauncherVisible(context, true)

    private fun hideCompletely(context: Context, op: Int) {
        if (op != operationSeq.get()) return
        Log.i(DIAG, "hideCompletely(op=$op) start oxygen=${isOxygenFamily()}")
        dumpHideDiagnostics(context, "before-hideCompletely")
        sendToHome(context)
        applyHideNow(context)
        scheduleHidePass(context, op, 400L, hard = false)
        scheduleHidePass(context, op, 1_200L, hard = true)
        scheduleHidePass(context, op, 3_000L, hard = true)
        scheduleHidePass(context, op, 6_000L, hard = true)
        mainHandler.postDelayed({
            if (op != operationSeq.get() || desiredVisible) return@postDelayed
            forceRestartHomeLauncher(context)
            dumpHideDiagnostics(context, "final-check-6.5s")
        }, 6_500L)
    }

    private fun scheduleHidePass(context: Context, op: Int, delayMs: Long, hard: Boolean) {
        mainHandler.postDelayed({
            if (op != operationSeq.get() || desiredVisible) {
                Log.i(DIAG, "hidePass@${delayMs}ms SKIPPED op=$op desiredVisible=$desiredVisible")
                return@postDelayed
            }
            Log.i(DIAG, "hidePass@${delayMs}ms hard=$hard stillHidden=${isLauncherHidden(context)}")
            if (hard) {
                applyHideHard(context)
            } else {
                applyHideNow(context)
            }
        }, delayMs)
    }

    private fun showCompletely(context: Context, op: Int, wasHidden: Boolean) {
        if (op != operationSeq.get()) return
        if (!desiredVisible || !loadDesired(context)) {
            Log.w(DIAG, "showCompletely aborted — hide desired")
            return
        }
        Log.i(DIAG, "showCompletely(op=$op wasHidden=$wasHidden)")
        applyShowNow(context)
        if (!wasHidden) return
        mainHandler.postDelayed({
            if (op != operationSeq.get() || !desiredVisible) return@postDelayed
            applyShowNow(context)
        }, 800L)
        mainHandler.postDelayed({
            if (op != operationSeq.get() || !desiredVisible) return@postDelayed
            applyShowNow(context)
            dumpHideDiagnostics(context, "after-show-retries")
        }, 2_500L)
    }

    private fun sendToHome(context: Context) {
        try {
            val home = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(home)
            Log.i(DIAG, "sent to HOME")
        } catch (e: Exception) {
            Log.e(DIAG, "Could not send to home", e)
        }
    }

    /**
     * Kill + restart the default home launcher so OxygenOS drops ghost icons
     * without rebooting the phone.
     */
    private fun forceRestartHomeLauncher(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val targets = linkedSetOf<String>()
        resolveDefaultHomePackage(context)?.let { targets.add(it) }
        targets.addAll(
            listOf(
                "net.oneplus.launcher",
                "com.oneplus.launcher",
                "com.android.launcher",
                "com.android.launcher3",
                "com.oppo.launcher",
                "com.oplus.launcher",
                "com.oplus.quickstep",
                "com.teslacoilsw.launcher"
            )
        )
        // Never kill ourselves.
        targets.remove(context.packageName)

        for (pkg in targets) {
            try {
                am.killBackgroundProcesses(pkg)
                Log.i(DIAG, "killBackgroundProcesses($pkg)")
            } catch (e: Exception) {
                Log.w(DIAG, "killBackgroundProcesses failed for $pkg", e)
            }
        }

        // Bring home back so launcher cold-starts and re-reads PackageManager.
        mainHandler.postDelayed({
            try {
                val home = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(home)
                Log.i(DIAG, "re-launched HOME after launcher kill")
            } catch (e: Exception) {
                Log.w(DIAG, "re-launch HOME failed", e)
            }
        }, 250L)
    }

    private fun resolveDefaultHomePackage(context: Context): String? {
        return try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val ri = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ri?.activityInfo?.packageName
        } catch (e: Exception) {
            Log.w(DIAG, "resolveDefaultHomePackage failed", e)
            null
        }
    }

    private fun removeFromRecents(context: Context) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val tasks = am.appTasks
                Log.i(DIAG, "removeFromRecents taskCount=${tasks.size}")
                for (task in tasks) {
                    try {
                        task.finishAndRemoveTask()
                    } catch (e: Exception) {
                        Log.w(DIAG, "finishAndRemoveTask failed", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(DIAG, "Could not clear recents", e)
        }
    }

    private fun componentState(pm: PackageManager, component: ComponentName): Int {
        return try {
            pm.getComponentEnabledSetting(component)
        } catch (e: Exception) {
            Log.w(DIAG, "getComponentEnabledSetting failed for ${component.flattenToShortString()}", e)
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
    }

    private fun applicationEnabledState(pm: PackageManager, pkg: String): Int {
        return try {
            pm.getApplicationEnabledSetting(pkg)
        } catch (e: Exception) {
            Log.w(DIAG, "getApplicationEnabledSetting failed", e)
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
    }

    /**
     * Exact hideunhide sample pattern:
     * `setApplicationEnabledSetting(DISABLED, DONT_KILL_APP)` when a launch intent exists.
     * Leaves the package disabled so OEM launchers drop the icon.
     *
     * Show / secret restore must call [ensureApplicationEnabled] first.
     * While package stays DISABLED, new background work will not start after process death.
     */
    private fun disableApplicationLikeSample(context: Context) {
        val pm = context.packageManager
        val pkg = context.packageName
        try {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            Log.i(
                DIAG,
                "disableApplicationLikeSample launchIntent=${launchIntent?.component?.flattenToShortString() ?: "NULL"}"
            )
            // Sample only disables when launch intent is non-null; we also disable when
            // launchers were already component-disabled (intent null) so OEM still drops icon.
            val before = stateName(applicationEnabledState(pm, pkg))
            pm.setApplicationEnabledSetting(
                pkg,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            val after = stateName(applicationEnabledState(pm, pkg))
            Log.i(DIAG, "setApplicationEnabledSetting DISABLED (sample) $before → $after")
        } catch (e: SecurityException) {
            // Sample catches this — common on locked-down ROMs.
            Log.w(DIAG, "setApplicationEnabledSetting SecurityException: ${e.message}")
        } catch (e: Exception) {
            Log.w(DIAG, "setApplicationEnabledSetting disable failed", e)
        }
    }

    /** Sample switch OFF — re-enable the whole package. */
    @JvmStatic
    fun ensureApplicationEnabled(context: Context) {
        val pm = context.packageManager
        val pkg = context.packageName
        try {
            val state = applicationEnabledState(pm, pkg)
            if (state != PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                && state != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            ) {
                pm.setApplicationEnabledSetting(
                    pkg,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                Log.i(DIAG, "ensureApplicationEnabled: was ${stateName(state)} → ENABLED")
            } else {
                // Force ENABLED like the sample (DEFAULT can still look "off" on some OEMs).
                pm.setApplicationEnabledSetting(
                    pkg,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                Log.i(DIAG, "ensureApplicationEnabled: force ENABLED (was ${stateName(state)})")
            }
        } catch (e: SecurityException) {
            Log.w(DIAG, "ensureApplicationEnabled SecurityException: ${e.message}")
        } catch (e: Exception) {
            Log.w(DIAG, "ensureApplicationEnabled failed", e)
        }
    }

    private fun enableChooseLoginActivity(context: Context) {
        setComponent(
            context,
            "com.busho.app.activities.ChooseLoginActivity",
            enabled = true,
            killApp = false
        )
    }

    private fun disableAllLauncherComponents(context: Context, killApp: Boolean) {
        val pkg = context.packageName
        val components = linkedSetOf(
            ComponentName(pkg, VISIBLE_ALIAS),
            ComponentName(pkg, BLANK_ALIAS),
            ComponentName(pkg, MAIN_LAUNCHER)
        )
        components.addAll(findLauncherComponents(context.packageManager, pkg, includeDisabled = true))
        components.addAll(findLauncherComponentsFromPackageInfo(context.packageManager, pkg))
        components.add(ComponentName(pkg, "$pkg.SecretLauncherAlias"))
        components.add(ComponentName(pkg, "$pkg.StealthLauncherAlias"))
        components.add(ComponentName(pkg, "$pkg.MainLauncherAlias"))

        Log.i(DIAG, "disableAllLauncherComponents killApp=$killApp count=${components.size}")
        for (c in components) {
            if (c.className.contains("ChooseLogin", ignoreCase = true)) continue
            setComponent(context, c.className, enabled = false, killApp = killApp)
        }
    }

    private fun setComponent(context: Context, className: String, enabled: Boolean, killApp: Boolean) {
        val pm = context.packageManager
        val component = ComponentName(context.packageName, className)
        if (!componentExists(pm, component)) {
            Log.i(DIAG, "SKIP missing ${component.flattenToShortString()}")
            return
        }
        val flags = if (killApp) 0 else PackageManager.DONT_KILL_APP
        val before = stateName(componentState(pm, component))
        try {
            // IMPORTANT: apps must use DISABLED — DISABLED_USER is ignored on OnePlus and
            // leaves aliases ENABLED (that caused the "LIVE launcher" + dead icon bug).
            val state = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            if (enabled) {
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    PackageManager.DONT_KILL_APP
                )
            }
            pm.setComponentEnabledSetting(component, state, flags)
            var afterState = componentState(pm, component)
            var after = stateName(afterState)
            Log.i(DIAG, "SET ${component.flattenToShortString()} $before → $after flags=$flags")

            // Retry without DONT_KILL_APP if disable did not stick.
            if (!enabled && afterState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                Log.w(DIAG, "disable did not stick — retry flags=0")
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    0
                )
                afterState = componentState(pm, component)
                after = stateName(afterState)
                Log.i(DIAG, "RETRY SET ${component.flattenToShortString()} → $after")
            }
        } catch (e: Exception) {
            Log.e(DIAG, "SET FAILED ${component.flattenToShortString()}", e)
        }
    }

    private fun componentExists(pm: PackageManager, component: ComponentName): Boolean {
        return try {
            @Suppress("DEPRECATION")
            pm.getActivityInfo(
                component,
                PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.GET_META_DATA
            )
            true
        } catch (_: PackageManager.NameNotFoundException) {
            // activity-alias also resolves via getActivityInfo on most APIs; if not, try anyway false
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun findLauncherComponents(
        pm: PackageManager,
        pkg: String,
        includeDisabled: Boolean
    ): Set<ComponentName> {
        val result = linkedSetOf<ComponentName>()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        var flags = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PackageManager.MATCH_ALL
        }
        if (includeDisabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            flags = flags or PackageManager.MATCH_DISABLED_COMPONENTS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                flags = flags or PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
            }
        }
        @Suppress("DEPRECATION")
        val infos = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
            } else {
                pm.queryIntentActivities(intent, flags)
            }
        } catch (e: Exception) {
            Log.w(DIAG, "queryIntentActivities failed", e)
            emptyList()
        }
        for (info in infos) {
            val ai = info.activityInfo ?: continue
            if (ai.packageName != pkg) continue
            result.add(ComponentName(ai.packageName, ai.name))
        }
        return result
    }

    private fun findLauncherComponentsFromPackageInfo(
        pm: PackageManager,
        pkg: String
    ): Set<ComponentName> {
        val result = linkedSetOf<ComponentName>()
        try {
            val flags = PackageManager.GET_ACTIVITIES or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    PackageManager.MATCH_DISABLED_COMPONENTS
                } else {
                    0
                })
            @Suppress("DEPRECATION")
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                pm.getPackageInfo(pkg, flags)
            }
            for (ai in pkgInfo.activities ?: emptyArray()) {
                val name = ai.name ?: continue
                if (name.contains("Launcher", ignoreCase = true)
                    || name == MAIN_LAUNCHER
                    || name.endsWith(".MainActivity")
                ) {
                    result.add(ComponentName(pkg, name))
                }
            }
        } catch (e: Exception) {
            Log.w(DIAG, "findLauncherComponentsFromPackageInfo failed", e)
        }
        // Always include known aliases
        result.add(ComponentName(pkg, VISIBLE_ALIAS))
        result.add(ComponentName(pkg, BLANK_ALIAS))
        return result
    }
}
