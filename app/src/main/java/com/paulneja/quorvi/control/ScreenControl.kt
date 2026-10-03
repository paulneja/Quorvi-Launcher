package com.paulneja.quorvi.control

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

enum class Arbitration { FREE, AGENT_EXCLUSIVE, USER_PREEMPTED, RELEASED }

data class UiNode(
    val index: Int,
    val role: String,
    val text: String,
    val id: String?,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val password: Boolean,
    val bounds: Rect,
)

data class UiSnapshot(val packageName: String, val nodes: List<UiNode>, val sensitive: Boolean) {
    fun describe(limit: Int = 120): String = buildString {
        append("screen of ").append(packageName)
        if (sensitive) append(" (sensitive: text in fields is hidden)")
        append('\n')
        nodes.take(limit).forEach { n ->
            append('#').append(n.index).append(' ').append(n.role)
            if (n.id != null) append(" id=").append(n.id)
            val shown = if (n.password) "••••" else n.text
            if (shown.isNotBlank()) append(" \"").append(shown.take(80)).append('"')
            val flags = buildList { if (n.clickable) add("click"); if (n.editable) add("edit"); if (n.scrollable) add("scroll") }
            if (flags.isNotEmpty()) append(" [").append(flags.joinToString(",")).append(']')
            append('\n')
        }
        if (nodes.size > limit) append("…(${nodes.size - limit} more)\n")
    }
}

sealed interface ScreenResult {
    data class Ok(val message: String) : ScreenResult
    data class Unavailable(val reason: String) : ScreenResult
    data object Preempted : ScreenResult
}

@Singleton
class ScreenControlBroker @Inject constructor() {
    private val service = AtomicReference<QuorviAccessibilityService?>(null)
    private val _state = MutableStateFlow(Arbitration.RELEASED)
    val state: StateFlow<Arbitration> = _state.asStateFlow()
    private val _foreground = MutableStateFlow("")
    val foreground: StateFlow<String> = _foreground.asStateFlow()

    val connected: Boolean get() = service.get() != null

    fun attach(s: QuorviAccessibilityService?) {
        service.set(s)
        _state.value = if (s != null) Arbitration.FREE else Arbitration.RELEASED
    }

    fun onForeground(pkg: String) {
        _foreground.value = pkg
    }

    fun userTouched() {
        if (_state.value == Arbitration.AGENT_EXCLUSIVE) _state.value = Arbitration.USER_PREEMPTED
    }

    fun acquire(): Boolean {
        val s = service.get() ?: return false
        if (_state.value == Arbitration.USER_PREEMPTED) return false
        _state.value = Arbitration.AGENT_EXCLUSIVE
        return true
    }

    fun release() {
        if (service.get() != null) _state.value = Arbitration.FREE
    }

    private inline fun <T> withService(onNone: () -> T, block: (QuorviAccessibilityService) -> T): T {
        val s = service.get() ?: return onNone()
        if (_state.value == Arbitration.USER_PREEMPTED) return onNone()
        return block(s)
    }

    fun inspect(): UiSnapshot? = service.get()?.snapshot()

    fun apps(): List<Pair<String, String>> = service.get()?.launchableApps().orEmpty()

    suspend fun launch(pkg: String): ScreenResult = withService({ ScreenResult.Unavailable("Screen control is off") }) {
        if (!it.launch(pkg)) return@withService ScreenResult.Unavailable("No launchable activity for $pkg")

        kotlinx.coroutines.withTimeoutOrNull(3_000) {
            while (_foreground.value != pkg) kotlinx.coroutines.delay(100)
        }
        ScreenResult.Ok("launched $pkg")
    }

    suspend fun act(action: String, target: String?, text: String?): ScreenResult {
        val s = service.get() ?: return ScreenResult.Unavailable("Screen control is off")
        if (_state.value == Arbitration.USER_PREEMPTED) return ScreenResult.Preempted
        return s.act(action, target, text)
    }

    suspend fun screenshot(): Bitmap? = service.get()?.screenshot()
}

@AndroidEntryPoint
class QuorviAccessibilityService : AccessibilityService() {
    @Inject lateinit var broker: ScreenControlBroker

    override fun onServiceConnected() {
        broker.attach(this)
    }

    override fun onDestroy() {
        broker.attach(null)
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                event.packageName?.let { broker.onForeground(it.toString()) }
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START, AccessibilityEvent.TYPE_GESTURE_DETECTION_START,
            AccessibilityEvent.TYPE_VIEW_CLICKED, AccessibilityEvent.TYPE_VIEW_SCROLLED ->
                broker.userTouched()
        }
    }

    fun snapshot(): UiSnapshot? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString() ?: "?"
        val nodes = mutableListOf<UiNode>()
        var sensitive = false
        var index = 0
        fun visit(node: AccessibilityNodeInfo?) {
            node ?: return
            if (nodes.size > 400) return
            val text = (node.text ?: node.contentDescription)?.toString().orEmpty()
            val relevant = node.isClickable || node.isEditable || node.isScrollable || (text.isNotBlank() && node.isVisibleToUser)
            if (relevant && node.isVisibleToUser) {
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                if (node.isPassword) sensitive = true
                nodes += UiNode(index++, role(node), text, node.viewIdResourceName?.substringAfterLast('/'), node.isClickable, node.isEditable, node.isScrollable, node.isPassword, bounds)
            }
            for (i in 0 until node.childCount) visit(node.getChild(i))
        }
        visit(root)
        return UiSnapshot(pkg, nodes, sensitive)
    }

    private fun role(node: AccessibilityNodeInfo): String = when {
        node.isEditable -> "field"
        node.className?.contains("Button") == true -> "button"
        node.className?.contains("Switch") == true || node.isCheckable -> "toggle"
        node.isScrollable -> "list"
        else -> "text"
    }

    fun launchableApps(): List<Pair<String, String>> = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
        .distinctBy { it.first }
        .sortedBy { it.second }

    fun launch(pkg: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    suspend fun act(action: String, target: String?, text: String?): ScreenResult {
        when (action) {
            "back" -> { performGlobalAction(GLOBAL_ACTION_BACK); return ScreenResult.Ok("back") }
            "home" -> { performGlobalAction(GLOBAL_ACTION_HOME); return ScreenResult.Ok("home") }
            "recents" -> { performGlobalAction(GLOBAL_ACTION_RECENTS); return ScreenResult.Ok("recents") }
            "notifications" -> { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); return ScreenResult.Ok("opened notifications") }
            "scroll_forward", "scroll_back" -> {
                val node = findScrollable() ?: return ScreenResult.Unavailable("No scrollable area")
                val ok = node.performAction(if (action == "scroll_forward") AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                return if (ok) ScreenResult.Ok(action) else ScreenResult.Unavailable("could not scroll")
            }
        }
        val node = target?.let { find(it) } ?: return ScreenResult.Unavailable("Target \"$target\" not found")
        return when (action) {
            "click" -> {
                val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (ok) ScreenResult.Ok("clicked \"$target\"") else ScreenResult.Unavailable("\"$target\" is not clickable")
            }
            "set_text" -> {
                if (node.isPassword) return ScreenResult.Unavailable("Nexo does not type into password fields")
                val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text.orEmpty()) }
                val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                if (ok) ScreenResult.Ok("typed into \"$target\"") else ScreenResult.Unavailable("could not type into \"$target\"")
            }
            else -> ScreenResult.Unavailable("Unknown action $action")
        }
    }

    private fun find(target: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        root.findAccessibilityNodeInfosByViewId(target).firstOrNull { it.isVisibleToUser }?.let { return it }
        val byText = root.findAccessibilityNodeInfosByText(target).filter { it.isVisibleToUser }
        byText.firstOrNull { it.isClickable }?.let { return it }
        byText.firstOrNull()?.let { node -> return clickableAncestor(node) ?: node }
        return null
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        repeat(5) { n = n?.parent; if (n?.isClickable == true) return n }
        return null
    }

    private fun findScrollable(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isScrollable && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
        }
        return null
    }

    @android.annotation.SuppressLint("NewApi")
    suspend fun screenshot(): Bitmap? {
        if (android.os.Build.VERSION.SDK_INT < 30) return null
        val deferred = CompletableDeferred<Bitmap?>()
        takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val bmp = runCatching { Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
                runCatching { result.hardwareBuffer.close() }
                deferred.complete(bmp)
            }
            override fun onFailure(errorCode: Int) { deferred.complete(null) }
        })
        return withTimeoutOrNull(5_000) { deferred.await() }
    }

    companion object {
        fun enabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val me = ComponentName(context, QuorviAccessibilityService::class.java).flattenToString()
            return flat.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
