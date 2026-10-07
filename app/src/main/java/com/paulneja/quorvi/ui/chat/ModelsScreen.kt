package com.paulneja.quorvi.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paulneja.quorvi.R
import com.paulneja.quorvi.agent.models.AiModelInfo
import com.paulneja.quorvi.agent.models.ProviderEntity
import com.paulneja.quorvi.agent.models.ReasoningEffort
import com.paulneja.quorvi.agent.orchestra.ModelBinding
import com.paulneja.quorvi.agent.orchestra.ModelRef
import com.paulneja.quorvi.agent.orchestra.Specialist
import com.paulneja.quorvi.ui.design.ChipButton
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.ModelPicker
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.QuorviSegmented

@Composable
fun ModelsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val o by vm.orchestra.collectAsState()
    val providers by vm.providerList.collectAsState()
    val models by vm.models.collectAsState()
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenBar(stringResource(R.string.models_title), onBack)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text(stringResource(R.string.models_intro), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(horizontal = 6.dp)) }
            item {
                Card {
                    UseHeader(Glyphs.Sparkle, stringResource(R.string.models_chat), stringResource(R.string.orc_coordinator_desc))
                    ModelChain(o.coordinator, providers, models, vm::loadModels, stringResource(R.string.orc_default_model)) { b -> vm.updateOrchestra { it.copy(coordinator = b) } }
                }
            }
            item { Text(stringResource(R.string.orc_specialists), style = Quorvi.type.section, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(start = 6.dp, top = 6.dp)) }
            item {
                Card {
                    ToggleLine(stringResource(R.string.orc_enable), stringResource(R.string.models_specialists_desc), o.enabled) { v -> vm.updateOrchestra { it.copy(enabled = v) } }
                }
            }
            Specialist.entries.forEach { s ->
                item(key = s.name) {
                    Card {
                        UseHeader(s.glyph(), specialistName(s.name), specialistBlurb(s))
                        ModelChain(o.specialists[s], providers, models, vm::loadModels, stringResource(R.string.orc_no_model)) { b ->
                            vm.updateOrchestra { it.copy(specialists = if (b == null) it.specialists - s else it.specialists + (s to b), active = if (b == null) it.active - s else it.active + s) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UseHeader(glyph: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Quorvi.colors.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Glyph(glyph, 17.dp, Quorvi.colors.accent) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text)
            Text(subtitle, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ModelChain(
    binding: ModelBinding?,
    providers: List<ProviderEntity>,
    models: Map<String, Result<List<AiModelInfo>>>,
    load: (String, Boolean) -> Unit,
    emptyText: String,
    onChange: (ModelBinding?) -> Unit,
) {
    if (providers.isEmpty()) {
        Text(stringResource(R.string.chat_no_model_body), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
        return
    }
    var adding by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf(providers.first().id) }
    LaunchedEffect(adding, provider) { if (adding) load(provider, false) }
    val chain = binding?.chain.orEmpty()
    fun save(list: List<ModelRef>) = onChange(if (list.isEmpty()) null else ModelBinding(list.first(), list.drop(1), binding?.effort, binding?.thinking ?: false))
    Column(Modifier.padding(top = 12.dp)) {
        if (chain.isEmpty()) Text(emptyText, style = Quorvi.type.caption, color = Quorvi.colors.textTertiary)
        chain.forEachIndexed { i, ref ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (i == 0) stringResource(R.string.models_primary) else stringResource(R.string.models_backup, i),
                    style = Quorvi.type.label.copy(fontWeight = FontWeight.SemiBold), color = if (i == 0) Quorvi.colors.accent else Quorvi.colors.textTertiary,
                    modifier = Modifier.width(84.dp),
                )
                Text(modelLabel(ref, providers), style = Quorvi.type.action, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (i > 0) SmallButton(Glyphs.Down, rotate = 180f) { save(chain.toMutableList().apply { add(i - 1, removeAt(i)) }) }
                SmallButton(Glyphs.Close) { save(chain - ref) }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(stringResource(if (adding) R.string.chat_cancel else if (chain.isEmpty()) R.string.models_choose else R.string.models_add)) { adding = !adding }
        }
        AnimatedVisibility(adding, enter = expandVertically(spring(dampingRatio = 0.8f)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    providers.forEach { p -> ChipButton(p.name, onClick = { provider = p.id }, active = p.id == provider) }
                }
                val result = models[provider]
                ModelPicker(
                    models = result?.getOrNull()?.filter { m -> chain.none { it.providerId == provider && it.model == m.id } },
                    selected = "",
                    onSelect = { model -> save((chain + ModelRef(provider, model)).distinct()); adding = false },
                    error = result?.exceptionOrNull()?.message,
                    onRetry = { load(provider, true) },
                )
            }
        }
        if (binding != null) {
            Text(stringResource(R.string.orc_effort), style = Quorvi.type.caption, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            QuorviSegmented(
                listOf<Pair<ReasoningEffort?, String>>(null to stringResource(R.string.orc_effort_auto), ReasoningEffort.LOW to stringResource(R.string.orc_effort_low), ReasoningEffort.MEDIUM to stringResource(R.string.orc_effort_medium), ReasoningEffort.HIGH to stringResource(R.string.orc_effort_high)),
                binding.effort, { e -> onChange(binding.copy(effort = e)) },
            )
        }
    }
}

@Composable
private fun SmallButton(glyph: ImageVector, rotate: Float = 0f, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Glyph(glyph, 14.dp, Quorvi.colors.textSecondary, Modifier.rotate(rotate))
    }
}
