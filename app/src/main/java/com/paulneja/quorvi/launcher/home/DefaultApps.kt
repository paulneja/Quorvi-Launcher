package com.paulneja.quorvi.launcher.home

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultApps @Inject constructor(@ApplicationContext private val context: Context) {
    private val pm = context.packageManager

    fun dockPackages(): List<String> = listOfNotNull(
        handler(Intent(Intent.ACTION_DIAL)),
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull() ?: handler(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)),
        handler(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)),
        handler(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
    ).distinct()

    private fun handler(intent: Intent): String? {
        val preferred = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        if (preferred != null && !isResolver(preferred)) return preferred
        return pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo }
            .sortedByDescending { it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
            .firstOrNull()?.packageName
    }

    private fun isResolver(pkg: String): Boolean = pkg == "android" || pkg.contains("resolver", ignoreCase = true)
}
