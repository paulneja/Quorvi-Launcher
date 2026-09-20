package com.paulneja.quorvi.labs

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.paulneja.quorvi.launcher.widgets.WidgetHostManager
import com.paulneja.quorvi.ui.theme.QuorviTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LabActivity : ComponentActivity() {
    @Inject lateinit var hostManager: WidgetHostManager
    private lateinit var widgets: WidgetLabController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        widgets = WidgetLabController(this, hostManager)
        setContent {
            QuorviTheme {
                Surface(Modifier.fillMaxSize()) {
                    var tab by rememberSaveable { mutableIntStateOf(0) }
                    Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                        PrimaryTabRow(selectedTabIndex = tab) {
                            listOf("Widgets", "Motion", "Termux", "Media").forEachIndexed { index, title ->
                                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                            }
                        }
                        when (tab) {
                            0 -> WidgetLab(widgets)
                            1 -> MotionLab()
                            2 -> TermuxLab()
                            else -> MediaLab()
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        widgets.startListening()
    }

    override fun onStop() {
        super.onStop()
        widgets.stopListening()
    }

    @Deprecated("AppWidgetHost.startAppWidgetConfigureActivityForResult reports here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        widgets.onActivityResult(requestCode, resultCode)
    }
}
