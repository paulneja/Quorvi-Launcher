package com.paulneja.quorvi

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.paulneja.quorvi.core.CrashGuard
import com.paulneja.quorvi.core.DefaultHome
import com.paulneja.quorvi.labs.LabActivity
import com.paulneja.quorvi.launcher.apps.IconCache
import com.paulneja.quorvi.launcher.apps.Shortcuts
import com.paulneja.quorvi.launcher.home.HomeRepository
import com.paulneja.quorvi.launcher.widgets.WidgetHostManager
import com.paulneja.quorvi.ui.canvas.CanvasCallbacks
import com.paulneja.quorvi.ui.canvas.CanvasMessage
import com.paulneja.quorvi.ui.canvas.CanvasScreen
import com.paulneja.quorvi.ui.canvas.CanvasViewModel
import com.paulneja.quorvi.ui.canvas.LocalIconCache
import com.paulneja.quorvi.ui.design.LocalWindowEffects
import com.paulneja.quorvi.ui.design.QuorviDesign
import com.paulneja.quorvi.ui.design.WindowEffects
import com.paulneja.quorvi.ui.safe.SafeHome
import com.paulneja.quorvi.ui.studio.StudioActivity
import com.paulneja.quorvi.ui.theme.QuorviTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeActivity : ComponentActivity() {
    @Inject lateinit var icons: IconCache
    @Inject lateinit var home: HomeRepository
    @Inject lateinit var widgets: WidgetHostManager
    @Inject lateinit var shortcuts: Shortcuts
    @Inject lateinit var core: com.paulneja.quorvi.ops.CoreClient
    @Inject lateinit var notificationHub: com.paulneja.quorvi.control.NotificationHub

    private val viewModel: CanvasViewModel by viewModels()

    private val homePresses = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    private var leftHome = false
    private var safeMode by mutableStateOf(false)
    private var isDefaultHome by mutableStateOf(true)
    private lateinit var effects: WindowEffects
    private var pendingWidget: PendingWidget? = null

    private data class PendingWidget(val id: Int, val info: AppWidgetProviderInfo, val page: Int, val spanX: Int, val spanY: Int)

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefaultHome = DefaultHome.isHeld(this)
        if (!isDefaultHome) runCatching { startActivity(DefaultHome.settingsIntent()) }
    }

    private val widgetBind = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = pendingWidget ?: return@registerForActivityResult
        if (result.resultCode == RESULT_OK) configureOrPlace(pending) else abandonWidget()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        preferHighestRefreshRate()
        safeMode = CrashGuard.inLoop(this)
        effects = WindowEffects(this).also { it.attach() }

        val callbacks = CanvasCallbacks(
            openSettings = { startActivity(com.paulneja.quorvi.ui.settings.SettingsActivity.intent(this)) },
            openStudio = { startActivity(com.paulneja.quorvi.ui.settings.SettingsActivity.intent(this, com.paulneja.quorvi.ui.settings.SettingsModule.HOME)) },
            openOperations = { startActivity(Intent(this, com.paulneja.quorvi.ui.operations.OperationsActivity::class.java)) },
            openAgents = { startActivity(com.paulneja.quorvi.ui.operations.OperationsActivity.agents(this)) },
            openHost = { startActivity(com.paulneja.quorvi.ui.operations.OperationsActivity.host(this, it)) },
            openAccounts = { startActivity(com.paulneja.quorvi.ui.settings.SettingsActivity.intent(this, com.paulneja.quorvi.ui.settings.SettingsModule.AI)) },
            openCapabilities = { startActivity(com.paulneja.quorvi.ui.settings.SettingsActivity.intent(this, com.paulneja.quorvi.ui.settings.SettingsModule.SYSTEM)) },
            openHttp = { startActivity(com.paulneja.quorvi.ui.operations.OperationsActivity.http(this, it)) },
            openCi = { id, bounds ->
                val intent = if (id != null) com.paulneja.quorvi.ui.operations.OperationsActivity.ci(this, id) else Intent(this, com.paulneja.quorvi.ui.operations.OperationsActivity::class.java)
                val options = android.app.ActivityOptions.makeScaleUpAnimation(window.decorView, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt().coerceAtLeast(1), bounds.height.toInt().coerceAtLeast(1))
                startActivity(intent, options.toBundle())
            },
            openLabs = { startActivity(Intent(this, LabActivity::class.java)) },
            openWallpaper = { runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.menu_wallpaper))) } },
            pickWidget = ::startWidget,
            makeDefault = { roleRequest.launch(DefaultHome.requestIntent(this)) },
        )

        setContent {
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            val look by viewModel.look.collectAsStateWithLifecycle()
            androidx.compose.runtime.LaunchedEffect(look.highRefresh) { preferHighestRefreshRate(look.highRefresh) }
            androidx.compose.runtime.LaunchedEffect(look.orientation) {
                requestedOrientation = when (look.orientation) {
                    com.paulneja.quorvi.core.settings.ScreenOrientation.AUTO -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                    com.paulneja.quorvi.core.settings.ScreenOrientation.PORTRAIT -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                    com.paulneja.quorvi.core.settings.ScreenOrientation.LANDSCAPE -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
            QuorviTheme {
                QuorviDesign(
                    profile = appearance.motion,
                    accent = ComposeColor(appearance.accent),
                    wallpaperTint = effects.wallpaperTint,
                    glass = appearance.glass,
                    look = look,
                ) {
                    CompositionLocalProvider(LocalIconCache provides icons, LocalWindowEffects provides effects) {
                        if (safeMode) {
                            SafeHome(
                                viewModel = viewModel,
                                onRetry = { CrashGuard.clear(this); safeMode = false },
                                onReset = { lifecycleScope.launch { home.clear(); CrashGuard.clear(this@HomeActivity); safeMode = false } },
                                onChooseHome = { runCatching { startActivity(DefaultHome.settingsIntent()) } },
                                onSettings = { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } },
                            )
                        } else {
                            CanvasScreen(
                                viewModel = viewModel,
                                widgets = widgets,
                                shortcuts = shortcuts,
                                homePresses = homePresses,
                                isDefaultHome = isDefaultHome,
                                callbacks = callbacks,
                            )
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { message ->
                    val text = when (message) {
                        CanvasMessage.NoSpace -> getString(R.string.no_space)
                        CanvasMessage.CannotPlace -> getString(R.string.cannot_place)
                        is CanvasMessage.LaunchFailed -> getString(R.string.launch_failed, message.label)
                        CanvasMessage.WallpaperFailed -> getString(R.string.wallpaper_failed)
                        is CanvasMessage.Error -> message.text
                    }
                    Toast.makeText(this@HomeActivity, text, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        widgets.startListening()
        core.attach()
        core.setVisible(true)
    }

    override fun onStop() {
        super.onStop()
        leftHome = true
        widgets.stopListening()
        core.setVisible(false)
        core.detach()
    }

    override fun onDestroy() {
        effects.detach()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()

        leftHome = false
        notificationHub.ensureConnected(this)
        isDefaultHome = DefaultHome.isHeld(this)
        viewModel.refreshWallpaper()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.tryEmit(!leftHome)
    }

    private fun startWidget(info: AppWidgetProviderInfo, page: Int, spanX: Int, spanY: Int) {
        val pending = PendingWidget(widgets.allocate(), info, page, spanX, spanY)
        pendingWidget = pending
        if (widgets.bindIfAllowed(pending.id, info)) {
            configureOrPlace(pending)
        } else {
            widgetBind.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending.id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile),
            )
        }
    }

    private fun configureOrPlace(pending: PendingWidget) {
        val info = widgets.info(pending.id) ?: pending.info
        if (widgets.needsConfiguration(info)) {
            runCatching { widgets.host.startAppWidgetConfigureActivityForResult(this, pending.id, 0, CONFIGURE_WIDGET, null) }
                .onFailure { abandonWidget() }
        } else {
            placeWidget(pending)
        }
    }

    private fun placeWidget(pending: PendingWidget) {
        pendingWidget = null
        viewModel.addWidget(pending.id, pending.spanX, pending.spanY, pending.page)
    }

    private fun abandonWidget() {
        pendingWidget?.let { widgets.release(it.id) }
        pendingWidget = null
    }

    @Deprecated("AppWidgetHost.startAppWidgetConfigureActivityForResult reports here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CONFIGURE_WIDGET) return
        val pending = pendingWidget ?: return
        if (resultCode == RESULT_OK) placeWidget(pending) else abandonWidget()
    }

    private fun preferHighestRefreshRate(enabled: Boolean = true) {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display ?: return
            else @Suppress("DEPRECATION") windowManager.defaultDisplay
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply { preferredDisplayModeId = if (enabled) best.modeId else 0 }
    }

    private companion object {
        const val CONFIGURE_WIDGET = 52
    }
}
