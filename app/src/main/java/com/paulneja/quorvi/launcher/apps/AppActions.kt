package com.paulneja.quorvi.launcher.apps

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppActions @Inject constructor(@ApplicationContext private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    fun launch(entry: AppEntry, bounds: Rect? = null, options: android.os.Bundle? = null): Boolean = runCatching {
        launcherApps.startMainActivity(entry.key.component, entry.user, bounds, options)
    }.isSuccess

    fun details(entry: AppEntry): Boolean = runCatching {
        launcherApps.startAppDetailsActivity(entry.key.component, entry.user, null, null)
    }.isSuccess

    fun uninstall(entry: AppEntry): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", entry.key.packageName, null))
            .putExtra(Intent.EXTRA_USER, entry.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.isSuccess
}
