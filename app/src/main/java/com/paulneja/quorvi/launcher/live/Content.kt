package com.paulneja.quorvi.launcher.live

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.util.Size
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

@Immutable
sealed interface Gated<out T> {
    data object NeedsPermission : Gated<Nothing>
    data class Ready<T>(val value: T) : Gated<T>
}

@Immutable
data class AgendaEvent(val title: String, val begin: Long, val end: Long, val allDay: Boolean)

data class EventRow(val title: String?, val begin: Long, val end: Long, val allDay: Boolean, val declined: Boolean)

object AgendaMap {
    fun upcoming(rows: List<EventRow>, now: Long, limit: Int = 4): List<AgendaEvent> = rows
        .filter { !it.declined && it.end > now && !it.title.isNullOrBlank() }
        .sortedWith(compareBy({ it.begin / 86_400_000L }, { it.allDay }, { it.begin }))
        .take(limit)
        .map { AgendaEvent(it.title!!.trim(), it.begin, it.end, it.allDay) }
}

@Immutable
data class StarredContact(val id: Long, val lookupKey: String, val name: String, val photo: ImageBitmap?) {
    val uri: Uri get() = ContactsContract.Contacts.getLookupUri(id, lookupKey)
}

@Immutable
data class RecentPhoto(val uri: Uri, val thumb: ImageBitmap?, val screenshot: Boolean)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class ContentSources @Inject constructor(@ApplicationContext private val context: Context, private val scope: CoroutineScope) {
    private val grants = MutableStateFlow(0)

    fun permissionsChanged() { grants.value++ }

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun <T> observing(uri: Uri, permission: () -> Boolean, read: () -> T) = grants.flatMapLatest {
        callbackFlow<Gated<T>> {
            if (!permission()) {
                trySend(Gated.NeedsPermission)
                awaitClose { }
                return@callbackFlow
            }
            fun push() { trySend(runCatching { Gated.Ready(read()) }.getOrElse { Gated.NeedsPermission }) }
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = push()
            }
            context.contentResolver.registerContentObserver(uri, true, observer)
            push()
            awaitClose { context.contentResolver.unregisterContentObserver(observer) }
        }.flowOn(Dispatchers.IO)
    }

    val agenda: StateFlow<Gated<List<AgendaEvent>>?> = observing(CalendarContract.CONTENT_URI, { granted(Manifest.permission.READ_CALENDAR) }) {
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - 86_400_000L)
            ContentUris.appendId(it, now + 2 * 86_400_000L)
        }.build()
        val rows = mutableListOf<EventRow>()
        context.contentResolver.query(
            uri,
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.SELF_ATTENDEE_STATUS),
            "${CalendarContract.Instances.VISIBLE} = 1",
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                rows += EventRow(c.getString(0), c.getLong(1), c.getLong(2), c.getInt(3) == 1, c.getInt(4) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED)
            }
        }
        AgendaMap.upcoming(rows, now)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    val starred: StateFlow<Gated<List<StarredContact>>?> = observing(ContactsContract.Contacts.CONTENT_URI, { granted(Manifest.permission.READ_CONTACTS) }) {
        val out = mutableListOf<StarredContact>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI),
            "${ContactsContract.Contacts.STARRED} = 1",
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
        )?.use { c ->
            while (c.moveToNext() && out.size < 8) {
                val photo = c.getString(3)?.let { p ->
                    runCatching { context.contentResolver.openInputStream(Uri.parse(p))?.use { android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
                }
                out += StarredContact(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), photo)
            }
        }
        out
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private fun photosGranted(): Boolean = when {
        else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val photos: StateFlow<Gated<List<RecentPhoto>>?> = observing(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ::photosGranted) {
        val out = mutableListOf<RecentPhoto>()
        val path = if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, path),
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext() && out.size < 6) {
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                val thumb = runCatching { context.contentResolver.loadThumbnail(uri, Size(320, 320), null).asImageBitmap() }.getOrNull()
                out += RecentPhoto(uri, thumb, c.getString(1).orEmpty().contains("Screenshot", ignoreCase = true))
            }
        }
        out
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
}
