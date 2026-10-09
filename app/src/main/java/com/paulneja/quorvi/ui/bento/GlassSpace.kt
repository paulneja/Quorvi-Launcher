package com.paulneja.quorvi.ui.bento

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paulneja.quorvi.R
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.liquidGlass

@Composable
fun GlassSpace(editing: Boolean, empty: Boolean) {
    Box(
        Modifier.fillMaxSize().padding(5.dp).liquidGlass(Quorvi.shapes.panel, GlassLevel.PANEL)
            .then(if (editing) Modifier.border(1.dp, Color.White.copy(alpha = 0.22f), Quorvi.shapes.panel) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (editing && empty) Text(stringResource(R.string.glass_space_hint), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(12.dp))
    }
}
