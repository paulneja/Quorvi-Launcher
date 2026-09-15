package com.paulneja.quorvi.core

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

object DefaultHome {
    fun isHeld(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true

    fun requestIntent(context: Context): Intent {
        val roles = context.getSystemService(RoleManager::class.java)
        return if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
        else settingsIntent()
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
