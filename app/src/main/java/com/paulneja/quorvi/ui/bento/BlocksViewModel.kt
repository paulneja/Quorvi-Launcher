package com.paulneja.quorvi.ui.bento

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paulneja.quorvi.control.ActiveNotification
import com.paulneja.quorvi.control.NotificationHub
import com.paulneja.quorvi.core.command.CommandDao
import com.paulneja.quorvi.launcher.apps.AppCatalog
import com.paulneja.quorvi.launcher.apps.AppEntry
import com.paulneja.quorvi.launcher.apps.Profile
import com.paulneja.quorvi.launcher.home.HomeRepository
import com.paulneja.quorvi.launcher.live.AgendaEvent
import com.paulneja.quorvi.launcher.live.ContentSources
import com.paulneja.quorvi.launcher.live.Gated
import com.paulneja.quorvi.launcher.live.MediaSessions
import com.paulneja.quorvi.launcher.live.MediaState
import com.paulneja.quorvi.launcher.live.Place
import com.paulneja.quorvi.launcher.live.RecentPhoto
import com.paulneja.quorvi.launcher.live.StarredContact
import com.paulneja.quorvi.launcher.live.WeatherSource
import com.paulneja.quorvi.launcher.live.WeatherState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotificationGroup(val packageName: String, val app: AppEntry?, val count: Int, val latest: ActiveNotification)

@HiltViewModel
class BlocksViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val media: MediaSessions,
    private val weatherSource: WeatherSource,
    private val content: ContentSources,
    private val hub: NotificationHub,
    private val home: HomeRepository,
    catalog: AppCatalog,
    commands: CommandDao,
    settings: com.paulneja.quorvi.core.settings.QuorviSettings,
) : ViewModel() {
    val mediaState: StateFlow<MediaState> = media.state
    val weather: StateFlow<WeatherState> = weatherSource.state
    val agenda: StateFlow<Gated<List<AgendaEvent>>?> = content.agenda
    val starred: StateFlow<Gated<List<StarredContact>>?> = content.starred
    val photos: StateFlow<Gated<List<RecentPhoto>>?> = content.photos
    val listenerConnected: StateFlow<Boolean> = hub.connected

    val notifications: StateFlow<List<NotificationGroup>> = combine(hub.active, catalog.apps) { list, apps ->
        val byPackage = apps.orEmpty().filter { it.profile == Profile.MAIN }.associateBy { it.key.packageName }
        list.filter { !it.ongoing }.groupBy { it.packageName }.map { (pkg, items) -> NotificationGroup(pkg, byPackage[pkg], items.size, items.first()) }
            .sortedByDescending { it.latest.postedAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val topApps: StateFlow<List<AppEntry>> = combine(commands.observeTopApps(24), catalog.apps, settings.hiddenApps, settings.unsuggested) { usage, apps, hidden, unsuggested ->
        com.paulneja.quorvi.core.command.Suggestions.rank(usage.map { it.key }, apps.orEmpty(), hidden, unsuggested, limit = 12, fill = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _places = MutableStateFlow<List<Place>>(emptyList())
    val places: StateFlow<List<Place>> = _places.asStateFlow()

    fun playPause() = media.playPause()
    fun next() = media.next()
    fun previous() = media.previous()
    fun seekTo(ms: Long) = media.seekTo(ms)
    fun openMedia() = media.open()

    fun retryWeather() = weatherSource.retry()
    fun searchPlaces(query: String) = viewModelScope.launch { _places.value = if (query.length < 2) emptyList() else weatherSource.search(query) }
    fun choosePlace(place: Place) = viewModelScope.launch { weatherSource.choose(place); _places.value = emptyList(); weatherSource.retry() }

    fun permissionsChanged() {
        content.permissionsChanged()
        weatherSource.retry()
    }

    fun dismiss(key: String) = hub.dismiss?.invoke(key)

    fun openPackage(pkg: String) {
        context.packageManager.getLaunchIntentForPackage(pkg)?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    fun saveConfig(id: Long, config: String?) = viewModelScope.launch { home.setConfig(id, config) }

    fun startTimer(id: Long, minutes: Int): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = runCatching { context.startActivity(intent) }.isSuccess
        if (ok) saveConfig(id, (System.currentTimeMillis() + minutes * 60_000L).toString())
        return ok
    }

    fun openTimers() {
        runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_TIMERS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
