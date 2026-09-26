package com.paulneja.quorvi.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paulneja.quorvi.R
import com.paulneja.quorvi.agent.models.AiModelInfo
import com.paulneja.quorvi.launcher.apps.AppSearch

@Composable
fun ModelPicker(models: List<AiModelInfo>?, selected: String, onSelect: (String) -> Unit, error: String? = null, onRetry: (() -> Unit)? = null) {
    var query by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        when {
            models == null && error == null -> Text(stringResource(R.string.models_loading), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
            error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.models_error, error), style = Quorvi.type.caption, color = Quorvi.colors.danger, modifier = Modifier.weight(1f))
                if (onRetry != null) Text(stringResource(R.string.models_retry), style = Quorvi.type.action, color = Quorvi.colors.accent, modifier = Modifier.clickable(onClick = onRetry).padding(6.dp))
            }
            else -> {
                val list = models.orEmpty()
                Row(Modifier.fillMaxWidth().clip(Quorvi.shapes.control).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(Glyphs.Search, 16.dp, Quorvi.colors.textSecondary)
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = Quorvi.type.body.copy(color = Quorvi.colors.text), cursorBrush = SolidColor(Quorvi.colors.accent),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner -> if (query.isEmpty()) Text(stringResource(R.string.models_search, list.size), style = Quorvi.type.body, color = Quorvi.colors.textSecondary); inner() },
                    )
                }
                val shown = if (query.isBlank()) list else list.filter { AppSearch.score(it.displayName, query) > 0 || it.id.contains(query, ignoreCase = true) }
                Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
                    shown.forEach { m ->
                        val on = m.id == selected
                        Row(
                            Modifier.fillMaxWidth().clip(Quorvi.shapes.control).background(if (on) Quorvi.colors.accent.copy(alpha = 0.16f) else Color.Transparent).clickable { onSelect(m.id) }.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.displayName, style = Quorvi.type.body.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (m.displayName != m.id) Text(m.id, style = Quorvi.type.mono, color = Quorvi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (m.vision) Text("vision", style = Quorvi.type.mono, color = Quorvi.colors.accent, modifier = Modifier.padding(end = 6.dp))
                            if (on) Glyph(Glyphs.Check, 16.dp, Quorvi.colors.accent)
                        }
                    }
                    if (shown.isEmpty()) Text(stringResource(R.string.models_none), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(6.dp))
                }
            }
        }
        Text(
            stringResource(if (manual) R.string.models_hide_manual else R.string.models_manual),
            style = Quorvi.type.caption, color = Quorvi.colors.accent,
            modifier = Modifier.padding(top = 8.dp).clickable { manual = !manual }.padding(vertical = 4.dp),
        )
        if (manual) InputField(selected, onSelect, stringResource(R.string.accounts_model))
    }
}
