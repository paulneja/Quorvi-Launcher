package com.paulneja.quorvi.core.command

import com.paulneja.quorvi.launcher.apps.AppEntry
import com.paulneja.quorvi.launcher.apps.Profile

object Suggestions {
    fun rank(usedKeys: List<String>, apps: List<AppEntry>, hidden: Set<String>, unsuggested: Set<String>, limit: Int, fill: Boolean): List<AppEntry> {
        val eligible = apps.filter { it.profile == Profile.MAIN && it.key.encode().let { k -> k !in hidden && k !in unsuggested } }
        val byKey = eligible.associateBy { "app:" + it.key.encode() }
        val used = usedKeys.mapNotNull { byKey[it] }.distinct()
        return (if (fill) used + eligible.filter { it !in used } else used).take(limit)
    }
}
