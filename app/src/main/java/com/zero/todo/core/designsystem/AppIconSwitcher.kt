package com.zero.todo.core.designsystem

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Keeps the launcher icon in sync with the in-app [ZeroAccent] by toggling
 * the four `<activity-alias>` launcher entries declared in the manifest.
 */
interface AppIconSwitcher {
    fun syncAccentIcon(accent: ZeroAccent)
}

object NoOpAppIconSwitcher : AppIconSwitcher {
    override fun syncAccentIcon(accent: ZeroAccent) = Unit
}

class AndroidAppIconSwitcher @Inject constructor(
    @ApplicationContext private val context: Context
) : AppIconSwitcher {

    private fun aliasFor(accent: ZeroAccent): String = when (accent) {
        ZeroAccent.SlateBlue -> ".LauncherSlateBlue"
        ZeroAccent.Monochrome -> ".LauncherMonochrome"
        ZeroAccent.Sage -> ".LauncherSage"
        ZeroAccent.WarmClay -> ".LauncherWarmClay"
    }

    override fun syncAccentIcon(accent: ZeroAccent) {
        runCatching {
            val pm = context.packageManager
            val pkg = context.packageName
            val all = ZeroAccent.entries.associateWith { ComponentName(pkg, pkg + aliasFor(it)) }
            val target = all.getValue(accent)

            fun isEnabled(a: ZeroAccent, component: ComponentName): Boolean {
                val state = pm.getComponentEnabledSetting(component)
                return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                    (a == ZeroAccent.SlateBlue && state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            }

            val targetEnabled = isEnabled(accent, target)
            val othersEnabled = all.filter { (a, c) -> a != accent && isEnabled(a, c) }
            if (targetEnabled && othersEnabled.isEmpty()) return@runCatching

            if (!targetEnabled) {
                pm.setComponentEnabledSetting(
                    target,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            othersEnabled.values.forEach { component ->
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
