package com.techteam.vpn.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.techteam.vpn.R

/** The launcher icons: each is an activity-alias in the manifest, and exactly one is enabled. */
object AppIcons {
    /** [preview] is the icon's bitmap foreground: Compose can't draw an adaptive icon. */
    data class Icon(val alias: String, val preview: Int, val name: Int)

    val all = listOf(
        Icon("IconDefault", R.mipmap.ic_launcher_foreground, R.string.icon_default),
        Icon("IconNeon", R.mipmap.ic_launcher_neon_foreground, R.string.icon_neon),
        Icon("IconGold", R.mipmap.ic_launcher_gold_foreground, R.string.icon_gold),
        Icon("IconWhite", R.mipmap.ic_launcher_white_foreground, R.string.icon_white),
    )

    private fun component(context: Context, alias: String) = ComponentName(context, "${context.packageName}.$alias")

    fun current(context: Context): String = all.firstOrNull { icon ->
        when (context.packageManager.getComponentEnabledSetting(component(context, icon.alias))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            // Untouched aliases keep the manifest's state: only the default one is enabled there.
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon.alias == "IconDefault"
            else -> false
        }
    }?.alias ?: "IconDefault"

    /** The launcher may take a few seconds to show the new icon. */
    fun set(context: Context, alias: String) {
        val pm = context.packageManager
        // Enable the new one first, so there is never a moment with no launcher entry.
        pm.setComponentEnabledSetting(component(context, alias), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        for (icon in all) {
            if (icon.alias == alias) continue
            pm.setComponentEnabledSetting(component(context, icon.alias), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
