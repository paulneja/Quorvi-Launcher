package com.paulneja.quorvi.ui.canvas

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paulneja.quorvi.R
import com.paulneja.quorvi.core.settings.AgentIndicator
import com.paulneja.quorvi.ops.RunEntity
import com.paulneja.quorvi.ops.RunState
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.liquidGlass

private val attention = setOf(RunState.WAITING_USER.name, RunState.WAITING_CAPABILITY.name, RunState.INTERRUPTED.name)

@Composable
fun AgentIndicatorBar(runs: List<RunEntity>, mode: AgentIndicator, onOpen: () -> Unit) {
    if (runs.isEmpty() || mode == AgentIndicator.OFF || mode == AgentIndicator.OPERATIONS_ONLY) return
    val needsYou = runs.firstOrNull { it.state in attention }

    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = if (mode == AgentIndicator.SUBTLE) Alignment.CenterEnd else Alignment.Center,
    ) {
        if (mode == AgentIndicator.SUBTLE) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onOpen),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (needsYou != null) Quorvi.colors.danger else Quorvi.colors.accent))
            }
            return@Box
        }
        Row(
            Modifier
                .widthIn(max = 360.dp)
                .liquidGlass(Quorvi.shapes.panel, GlassLevel.CHROME)
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Image(
                rememberVectorPainter(Glyphs.Agent), null, Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(if (needsYou != null) Quorvi.colors.danger else Quorvi.colors.accent),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                val title = when {
                    needsYou?.state == RunState.WAITING_USER.name -> stringResource(R.string.agent_needs_answer)
                    needsYou?.state == RunState.WAITING_CAPABILITY.name -> stringResource(R.string.agent_needs_capability)
                    needsYou != null -> stringResource(R.string.agent_interrupted)
                    else -> pluralStringResource(R.plurals.agents_working, runs.size, runs.size)
                }
                Text(title, style = Quorvi.type.caption, color = Quorvi.colors.text, maxLines = 1)
                if (mode == AgentIndicator.VERBOSE) {
                    Text((needsYou ?: runs.first()).goal, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
