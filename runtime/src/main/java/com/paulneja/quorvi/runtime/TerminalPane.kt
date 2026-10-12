package com.paulneja.quorvi.runtime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File
import kotlin.concurrent.thread

class TerminalPane(
    context: Context,
    private val title: String? = "Terminal",
    private val transparent: Boolean = false,
    private val onStorage: (() -> Unit)? = null,
    private val onExit: () -> Unit = {},
) : LinearLayout(context) {
    private val bootstrap = Bootstrap(context)
    private val content = FrameLayout(context)
    private var terminal: TerminalView? = null
    private var session: TerminalSession? = null
    private var fontSize = (13 * resources.displayMetrics.scaledDensity).toInt()
    private var ctrl = false
    private var alt = false
    private val modifierViews = mutableMapOf<String, TextView>()
    private val main = Handler(Looper.getMainLooper())
    private val mono: Typeface by lazy { runCatching { resources.getFont(R.font.geist_mono_regular) }.getOrDefault(Typeface.MONOSPACE) }
    private var started = false

    init {
        orientation = VERTICAL
        if (!transparent) setBackgroundColor(BG)
        if (title != null) addView(topBar(title))
        addView(content, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(extraKeys())
    }

    fun start() {
        if (started) return
        started = true
        if (bootstrap.ready) {
            thread(name = "nexo-hooks") { runCatching { bootstrap.install {} } }
            startSession()
        } else {
            install()
        }
    }

    fun release() {
        session?.finishIfRunning()
        session = null
    }

    fun write(text: String) = session?.write(text)

    fun linkStorage() {
        val shared = android.os.Environment.getExternalStorageDirectory()
        val dir = File(bootstrap.home, "storage").apply { mkdirs() }
        val links = mapOf(
            "shared" to shared,
            "downloads" to File(shared, android.os.Environment.DIRECTORY_DOWNLOADS),
            "dcim" to File(shared, android.os.Environment.DIRECTORY_DCIM),
            "pictures" to File(shared, android.os.Environment.DIRECTORY_PICTURES),
            "music" to File(shared, android.os.Environment.DIRECTORY_MUSIC),
            "movies" to File(shared, android.os.Environment.DIRECTORY_MOVIES),
        )
        val made = links.count { (name, target) ->
            val link = File(dir, name)
            runCatching { if (link.exists() || link.isSymlink()) link.delete(); Os.symlink(target.absolutePath, link.absolutePath) }.isSuccess
        }
        Toast.makeText(context, "~/storage ready ($made links)", Toast.LENGTH_SHORT).show()
        session?.write("ls ~/storage\r")
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun pill(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun topBar(label: String): View = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(10), dp(8), dp(8))
        addView(TextView(context).apply {
            text = label
            setTextColor(TEXT)
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (onStorage != null) addView(barButton("sdcard") { onStorage.invoke() })
        addView(barButton("A−") { resize(-1) })
        addView(barButton("A+") { resize(1) })
    }

    private fun barButton(label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        setTextColor(TEXT)
        textSize = 13f
        typeface = mono
        gravity = Gravity.CENTER
        background = pill(SURFACE, 10)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) }
        setOnClickListener { onClick() }
    }

    private fun extraKeys(): View = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(6))
        listOf(
            listOf("ESC", "/", "-", "HOME", "↑", "END", "PGUP"),
            listOf("TAB", "CTRL", "ALT", "←", "↓", "→", "PGDN"),
        ).forEach { keys ->
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                keys.forEach { key -> addView(keyButton(key), LayoutParams(0, dp(42), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }) }
            })
        }
    }

    private fun keyButton(key: String) = TextView(context).apply {
        text = key
        setTextColor(TEXT)
        textSize = 13f
        typeface = mono
        gravity = Gravity.CENTER
        background = pill(SURFACE, 10)
        isHapticFeedbackEnabled = true
        if (key == "CTRL" || key == "ALT") modifierViews[key] = this
        setOnClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); press(key) }
    }

    private fun press(key: String) {
        val view = terminal ?: return
        when (key) {
            "CTRL" -> { ctrl = !ctrl; paintModifiers(); return }
            "ALT" -> { alt = !alt; paintModifiers(); return }
            "/", "-" -> { session?.write(key); clearModifiers(); return }
        }
        val code = when (key) {
            "ESC" -> KeyEvent.KEYCODE_ESCAPE
            "TAB" -> KeyEvent.KEYCODE_TAB
            "HOME" -> KeyEvent.KEYCODE_MOVE_HOME
            "END" -> KeyEvent.KEYCODE_MOVE_END
            "PGUP" -> KeyEvent.KEYCODE_PAGE_UP
            "PGDN" -> KeyEvent.KEYCODE_PAGE_DOWN
            "↑" -> KeyEvent.KEYCODE_DPAD_UP
            "↓" -> KeyEvent.KEYCODE_DPAD_DOWN
            "←" -> KeyEvent.KEYCODE_DPAD_LEFT
            "→" -> KeyEvent.KEYCODE_DPAD_RIGHT
            else -> return
        }
        var meta = 0
        if (ctrl) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (alt) meta = meta or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        val now = System.currentTimeMillis()
        view.onKeyDown(code, KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
        view.onKeyUp(code, KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
        clearModifiers()
    }

    private fun clearModifiers() {
        if (!ctrl && !alt) return
        ctrl = false
        alt = false
        paintModifiers()
    }

    private fun paintModifiers() {
        modifierViews["CTRL"]?.background = pill(if (ctrl) ACCENT else SURFACE, 10)
        modifierViews["CTRL"]?.setTextColor(if (ctrl) ON_ACCENT else TEXT)
        modifierViews["ALT"]?.background = pill(if (alt) ACCENT else SURFACE, 10)
        modifierViews["ALT"]?.setTextColor(if (alt) ON_ACCENT else TEXT)
    }

    private fun resize(step: Int) {
        val density = resources.displayMetrics.scaledDensity
        fontSize = (fontSize + step * density * 1.5f).toInt().coerceIn((8 * density).toInt(), (32 * density).toInt())
        terminal?.setTextSize(fontSize)
    }

    private fun File.isSymlink(): Boolean = runCatching { java.nio.file.Files.isSymbolicLink(toPath()) }.getOrDefault(false)

    private fun install() {
        val label = TextView(context).apply {
            setTextColor(MUTED)
            gravity = Gravity.CENTER
            typeface = mono
            text = "Preparing the runtime…"
        }
        content.addView(label)
        thread(name = "nexo-bootstrap") {
            val result = runCatching { bootstrap.install { stage -> main.post { label.text = "Runtime: $stage" } } }
            main.post {
                result.onSuccess { content.removeView(label); startSession() }
                    .onFailure { label.text = "Runtime install failed\n${it.message}" }
            }
        }
    }

    private fun startSession() {
        val env = Environment.of(bootstrap).map { (k, v) -> "$k=$v" }.toTypedArray()
        val shell = listOf("bin/bash", "bin/sh").map { File(bootstrap.prefix, it) }.first { it.exists() }
        val created = TerminalSession(shell.absolutePath, bootstrap.home.absolutePath, arrayOf("-l"), env, 3000, sessionClient)
        session = created
        val view = TerminalView(context, null).apply {
            setTerminalViewClient(viewClient)
            setTextSize(fontSize)
            setTypeface(mono)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            isFocusable = true
            isFocusableInTouchMode = true
            attachSession(created)
        }
        terminal = view
        content.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        view.requestFocus()
    }

    private fun showKeyboard() {
        val view = terminal ?: return
        view.requestFocus()
        context.getSystemService(InputMethodManager::class.java).showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private val sessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) { terminal?.onScreenUpdated() }
        override fun onTitleChanged(changedSession: TerminalSession) = Unit
        override fun onSessionFinished(finishedSession: TerminalSession) {
            terminal?.let { content.removeView(it) }
            terminal = null
            session = null
            started = false
            onExit()
        }
        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("terminal", text.orEmpty()))
        }
        override fun onPasteTextFromClipboard(session: TerminalSession?) {
            val text = context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            if (!text.isNullOrEmpty()) terminal?.mEmulator?.paste(text)
        }
        override fun onBell(session: TerminalSession) = Unit
        override fun onColorsChanged(session: TerminalSession) = Unit
        override fun onTerminalCursorStateChange(state: Boolean) = Unit
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String?, message: String?) = Unit
        override fun logWarn(tag: String?, message: String?) = Unit
        override fun logInfo(tag: String?, message: String?) = Unit
        override fun logDebug(tag: String?, message: String?) = Unit
        override fun logVerbose(tag: String?, message: String?) = Unit
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = Unit
        override fun logStackTrace(tag: String?, e: Exception?) = Unit
    }

    private val viewClient = object : TerminalViewClient {
        override fun onScale(scale: Float): Float {
            if (scale < 0.9f || scale > 1.1f) {
                resize(if (scale > 1f) 1 else -1)
                return 1f
            }
            return scale
        }
        override fun onSingleTapUp(e: MotionEvent?) = showKeyboard()
        override fun shouldBackButtonBeMappedToEscape() = false
        override fun shouldEnforceCharBasedInput() = true
        override fun shouldUseCtrlSpaceWorkaround() = false
        override fun isTerminalViewSelected() = true
        override fun copyModeChanged(copyMode: Boolean) = Unit
        override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?) = false
        override fun onKeyUp(keyCode: Int, e: KeyEvent?) = false
        override fun onLongPress(event: MotionEvent?) = false
        override fun readControlKey() = ctrl
        override fun readAltKey() = alt
        override fun readShiftKey() = false
        override fun readFnKey() = false
        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
            if (ctrl || alt) main.post { clearModifiers() }
            return false
        }
        override fun onEmulatorSet() = Unit
        override fun logError(tag: String?, message: String?) = Unit
        override fun logWarn(tag: String?, message: String?) = Unit
        override fun logInfo(tag: String?, message: String?) = Unit
        override fun logDebug(tag: String?, message: String?) = Unit
        override fun logVerbose(tag: String?, message: String?) = Unit
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = Unit
        override fun logStackTrace(tag: String?, e: Exception?) = Unit
    }

    companion object {
        val BG = Color.parseColor("#0C0F14")
        val SURFACE = Color.parseColor("#1A1F27")
        val TEXT = Color.parseColor("#F4F5F7")
        val MUTED = Color.parseColor("#A3A6AB")
        val ACCENT = Color.parseColor("#5EA7FF")
        val ON_ACCENT = Color.parseColor("#08121F")
    }
}
