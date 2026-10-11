package com.paulneja.quorvi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.paulneja.quorvi.ui.chat.LegacyOrb
import com.paulneja.quorvi.ui.chat.orb.NexoOrb
import com.paulneja.quorvi.ui.chat.orb.OrbMode
import com.paulneja.quorvi.ui.chat.orb.OrbState
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.QuorviDesign
import com.paulneja.quorvi.ui.theme.QuorviTheme
import kotlin.math.abs
import kotlin.math.sin

class OrbLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val startMode = if (intent.getStringExtra("mode") == "ultra") OrbMode.ULTRA else OrbMode.CHAT
        val startState = intent.getStringExtra("state")?.let { s -> OrbState.entries.firstOrNull { it.name == s } } ?: OrbState.IDLE
        val legacy = intent.getBooleanExtra("legacy", false)
        setContent {
            QuorviTheme {
                QuorviDesign {
                    var mode by remember { mutableStateOf(startMode) }
                    var state by remember { mutableStateOf(startState) }
                    var level by remember { mutableFloatStateOf(0f) }
                    LaunchedEffect(state) {
                        val t0 = withFrameNanos { it }
                        while (true) withFrameNanos {
                            val t = (it - t0) / 1e9f

                            level = if (state == OrbState.LISTENING || state == OrbState.SPEAKING) (abs(sin(t * 7.3f)) * (0.45f + 0.55f * abs(sin(t * 1.3f)))) else 0f
                        }
                    }
                    Column(
                        Modifier.fillMaxSize().background(Color(0xFF05060A)).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(24.dp))
                        Box(Modifier.height(300.dp), contentAlignment = Alignment.Center) {
                            if (legacy) LegacyOrb(220.dp, mode == OrbMode.ULTRA, state == OrbState.THINKING)
                            else NexoOrb(280.dp, mode, state, level)
                        }
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            NexoOrb(104.dp, mode, state, level)
                            NexoOrb(34.dp, mode, state, level)
                            NexoOrb(28.dp, mode, OrbState.IDLE, animate = false)
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OrbMode.entries.forEach { m -> Pick(m.name, mode == m) { mode = m } }
                        }
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OrbState.entries.forEach { s -> Pick(s.name.take(5), state == s) { state = s } }
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Pick(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, style = Quorvi.type.caption, color = if (on) Color.Black else Color.White,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) Color.White else Color.White.copy(alpha = 0.1f)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
    )
}
