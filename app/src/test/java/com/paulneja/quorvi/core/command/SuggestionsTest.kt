package com.paulneja.quorvi.core.command

import android.os.Process
import com.paulneja.quorvi.launcher.apps.AppEntry
import com.paulneja.quorvi.launcher.apps.AppKey
import com.paulneja.quorvi.launcher.apps.Profile
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SuggestionsTest {
    private fun app(name: String, profile: Profile = Profile.MAIN) = AppEntry(AppKey("pkg.$name", "Main", 0), name, Process.myUserHandle(), profile)
    private val maps = app("Maps")
    private val bank = app("Bank")
    private val camera = app("Camera")
    private val secret = app("Secret", Profile.PRIVATE)
    private val apps = listOf(bank, camera, maps, secret)
    private fun key(a: AppEntry) = "app:" + a.key.encode()

    @Test
    fun unsuggestedAppsNeverAppearEvenIfMostUsed() {
        val ranked = Suggestions.rank(listOf(key(bank), key(maps)), apps, hidden = emptySet(), unsuggested = setOf(bank.key.encode()), limit = 8, fill = false)
        assertEquals(listOf(maps), ranked)
    }

    @Test
    fun hiddenAndPrivateAppsAreLeftOutOfTheFiller() {
        val ranked = Suggestions.rank(listOf(key(secret)), apps, hidden = setOf(camera.key.encode()), unsuggested = emptySet(), limit = 8, fill = true)
        assertEquals(listOf(bank, maps), ranked)
    }

    @Test
    fun usedFirstThenFillerUpToTheLimit() {
        val ranked = Suggestions.rank(listOf(key(maps), key(maps)), apps, hidden = emptySet(), unsuggested = emptySet(), limit = 2, fill = true)
        assertEquals(listOf(maps, bank), ranked)
    }
}
