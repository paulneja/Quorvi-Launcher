package com.paulneja.quorvi.launcher.apps

import android.content.ComponentName
import android.os.UserHandle

data class AppKey(val packageName: String, val className: String, val userSerial: Long) {
    val component: ComponentName get() = ComponentName(packageName, className)

    fun encode(): String = "$packageName/$className#$userSerial"

    companion object {
        fun decode(value: String): AppKey? {
            val slash = value.indexOf('/')
            val hash = value.lastIndexOf('#')
            if (slash <= 0 || hash <= slash + 1) return null
            val serial = value.substring(hash + 1).toLongOrNull() ?: return null
            return AppKey(value.substring(0, slash), value.substring(slash + 1, hash), serial)
        }
    }
}

enum class Profile { MAIN, WORK, PRIVATE }

data class ProfileState(val user: UserHandle, val serial: Long, val profile: Profile, val paused: Boolean)

data class AppEntry(
    val key: AppKey,
    val label: String,
    val user: UserHandle,
    val profile: Profile = Profile.MAIN,
    val paused: Boolean = false,
) {
    val work: Boolean get() = profile == Profile.WORK
}
