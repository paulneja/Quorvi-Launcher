package com.paulneja.quorvi.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import com.paulneja.quorvi.agent.orchestra.Orchestra
import com.paulneja.quorvi.agent.orchestra.Specialist
import com.paulneja.quorvi.ui.design.ChipButton
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.ModelPicker
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.QuorviSegmented
import com.paulneja.quorvi.ui.design.QuorviSlider
import com.paulneja.quorvi.ui.design.QuorviSwitch
import com.paulneja.quorvi.ui.design.liquidGlass
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

fun Specialist.glyph(): ImageVector = when (this) {
    Specialist.THINK -> Glyphs.Brain
    Specialist.PLAN -> Glyphs.Note
    Specialist.RESEARCH -> Glyphs.Globe
    Specialist.CODE -> Glyphs.Terminal
    Specialist.WRITE -> Glyphs.Pen
    Specialist.IMAGE -> Glyphs.Image
    Specialist.REVIEW -> Glyphs.Check
}

@Composable
fun specialistBlurb(s: Specialist): String = stringResource(
    when (s) {
        Specialist.THINK -> R.string.sp_think_desc
        Specialist.PLAN -> R.string.sp_plan_desc
        Specialist.RESEARCH -> R.string.sp_research_desc
        Specialist.CODE -> R.string.sp_code_desc
        Specialist.WRITE -> R.string.sp_write_desc
        Specialist.IMAGE -> R.string.sp_image_desc
        Specialist.REVIEW -> R.string.sp_review_desc
    },
)

@Composable
fun OrchestraScreen(vm: ChatViewModel, onBack: () -> Unit, onModels: () -> Unit) {
    val o by vm.orchestra.collectAsState()
    val providers by vm.providerList.collectAsState()
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenBar(stringResource(R.string.orc_title), onBack)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { OrchestraDiagram(o) }
            item {
                Card {
                    Text(stringResource(R.string.orc_how_title), style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text)
                    listOf(R.string.orc_how_1, R.string.orc_how_2, R.string.orc_how_3, R.string.orc_how_4).forEachIndexed { i, s ->
                        Row(Modifier.padding(top = 8.dp)) {
                            Text("${i + 1}", style = Quorvi.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.onAccent, modifier = Modifier.size(20.dp).clip(CircleShape).background(Quorvi.colors.accent).padding(top = 2.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(s), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                Card {
                    Label(stringResource(R.string.orc_coordinator), stringResource(R.string.orc_coordinator_desc), Glyphs.Sparkle)
                    ModelSummary(o.coordinator, providers, stringResource(R.string.orc_default_model), onModels)
                }
            }
            item {
                Card {
                    ToggleLine(stringResource(R.string.orc_enable), stringResource(R.string.orc_enable_desc), o.enabled) { v -> vm.updateOrchestra { it.copy(enabled = v) } }
                }
            }
            if (o.enabled) {
                item { Text(stringResource(R.string.orc_specialists), style = Quorvi.type.section, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(start = 6.dp, top = 6.dp)) }
                Specialist.entries.forEach { s ->
                    item(key = s.name) { SpecialistCard(s, o, providers, vm, onModels) }
                }
                item {
                    Card {
                        val reviewReady = o.specialists[Specialist.REVIEW] != null && Specialist.REVIEW in o.active
                        ToggleLine(stringResource(R.string.orc_final_review), stringResource(if (reviewReady) R.string.orc_final_review_desc else R.string.orc_final_review_needs), o.finalReview, enabled = reviewReady) { v -> vm.updateOrchestra { it.copy(finalReview = v) } }
                    }
                }
            }
            item { Text(stringResource(R.string.orc_behaviour), style = Quorvi.type.section, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(start = 6.dp, top = 6.dp)) }
            item {
                Card {
                    ToggleLine(stringResource(R.string.orc_tasks), stringResource(R.string.orc_tasks_desc), o.backgroundTasks) { v -> vm.updateOrchestra { it.copy(backgroundTasks = v) } }
                    ToggleLine(stringResource(R.string.orc_past), stringResource(R.string.orc_past_desc), o.pastChats) { v -> vm.updateOrchestra { it.copy(pastChats = v) } }
                    ToggleLine(stringResource(R.string.mem_use), stringResource(R.string.mem_use_desc), o.memory) { v -> vm.updateOrchestra { it.copy(memory = v) } }
                    ToggleLine(stringResource(R.string.mem_auto), stringResource(R.string.mem_auto_desc), o.autoMemory, enabled = o.memory) { v -> vm.updateOrchestra { it.copy(autoMemory = v) } }
                    ToggleLine(stringResource(R.string.orc_unlimited), stringResource(R.string.orc_unlimited_desc), o.unlimited) { v -> vm.updateOrchestra { it.copy(maxRounds = if (v) Orchestra.UNLIMITED else 12) } }
                    if (!o.unlimited) {
                        var rounds by remember(o.maxRounds) { mutableStateOf(o.maxRounds.toFloat()) }
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.orc_rounds), style = Quorvi.type.action, color = Quorvi.colors.text, modifier = Modifier.weight(1f))
                            Text("${rounds.roundToInt()}", style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.accent)
                        }
                        QuorviSlider(rounds, { rounds = it }, Modifier.padding(top = 6.dp), range = 2f..40f, onDone = { vm.updateOrchestra { it.copy(maxRounds = rounds.roundToInt()) } })
                    }
                }
            }
            item {
                Card {
                    Label(stringResource(R.string.orc_instructions), stringResource(R.string.orc_instructions_desc), Glyphs.Edit)
                    var text by remember(o.instructions) { mutableStateOf(o.instructions) }
                    BasicTextField(
                        text, { text = it }, textStyle = Quorvi.type.body.copy(color = Quorvi.colors.text), cursorBrush = SolidColor(Quorvi.colors.accent), minLines = 3,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f)).padding(12.dp),
                        decorationBox = { inner -> Box { if (text.isEmpty()) Text(stringResource(R.string.orc_instructions_hint), style = Quorvi.type.body, color = Quorvi.colors.textTertiary); inner() } },
                    )
                    AnimatedVisibility(text != o.instructions) {
                        Row(Modifier.padding(top = 10.dp)) { Pill(stringResource(R.string.chat_save), accent = true) { vm.updateOrchestra { it.copy(instructions = text) } } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecialistCard(s: Specialist, o: Orchestra, providers: List<ProviderEntity>, vm: ChatViewModel, onModels: () -> Unit) {
    val binding = o.specialists[s]
    val on = s in o.active && binding != null
    var open by rememberSaveable(s) { mutableStateOf(false) }
    val tint by animateColorAsState(if (on) Quorvi.colors.accent else Quorvi.colors.textTertiary, tween(220), label = "tint")
    Card {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { Glyph(s.glyph(), 19.dp, tint) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(specialistName(s.name), style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text)
                Text(binding?.let { b -> modelLabel(b.primary, providers) } ?: stringResource(R.string.orc_no_model), style = Quorvi.type.caption, color = if (binding == null) Quorvi.colors.textTertiary else Quorvi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            QuorviSwitch(on, { v -> if (binding == null) onModels() else vm.updateOrchestra { it.copy(active = if (v) it.active + s else it.active - s) } })
        }
        AnimatedVisibility(open, enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 10.dp)) {
                Text(specialistBlurb(s), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
                ModelSummary(binding, providers, stringResource(R.string.orc_no_model), onModels)
            }
        }
    }
}

internal fun modelLabel(ref: ModelRef, providers: List<ProviderEntity>) = (providers.firstOrNull { it.id == ref.providerId }?.name?.let { "$it · " } ?: "") + ref.model

@Composable
private fun ModelSummary(binding: ModelBinding?, providers: List<ProviderEntity>, emptyText: String, onModels: () -> Unit) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(Glyphs.Model, 16.dp, if (binding == null) Quorvi.colors.textTertiary else Quorvi.colors.accent)
        Spacer(Modifier.width(8.dp))
        Text(
            binding?.let { b -> modelLabel(b.primary, providers) + if (b.fallbacks.isNotEmpty()) " +${b.fallbacks.size}" else "" } ?: emptyText,
            style = Quorvi.type.caption, color = if (binding == null) Quorvi.colors.textTertiary else Quorvi.colors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Pill(stringResource(R.string.models_choose_short), onClick = onModels)
    }
}

@Composable
private fun OrchestraDiagram(o: Orchestra) {
    val t = rememberInfiniteTransition(label = "flow")
    val flow by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "f")
    val accent = Quorvi.colors.accent
    val dim = Quorvi.colors.textTertiary
    val text = Quorvi.colors.text
    val crew = Specialist.entries.filter { it != Specialist.REVIEW }
    val painters = crew.associateWith { rememberVectorPainter(it.glyph()) }
    val coordinatorIcon = rememberVectorPainter(Glyphs.Sparkle)
    val youIcon = rememberVectorPainter(Glyphs.Contact)
    val answerIcon = rememberVectorPainter(Glyphs.Check)
    val reviewIcon = rememberVectorPainter(Glyphs.Check)
    val bouncy by animateFloatAsState(if (o.enabled) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 120f), label = "on")
    val on = bouncy.coerceIn(0f, 1.15f)
    Column(Modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(26.dp), GlassLevel.PANEL).padding(16.dp)) {
        Canvas(Modifier.fillMaxWidth().height(220.dp)) {
            val w = size.width
            val h = size.height
            val you = Offset(w * 0.08f, h * 0.5f)
            val hub = Offset(w * 0.5f, h * 0.5f)
            val out = Offset(w * 0.92f, h * 0.5f)
            fun line(a: Offset, b: Offset, active: Boolean, alpha: Float = 1f) {
                drawLine((if (active) accent else dim).copy(alpha = ((if (active) 0.55f else 0.18f) * alpha).coerceIn(0f, 1f)), a, b, 2.dp.toPx(), StrokeCap.Round)
            }
            fun packet(a: Offset, b: Offset, phase: Float, alpha: Float = 1f) {
                val p = ((flow + phase) % 1f)
                val pos = Offset(a.x + (b.x - a.x) * p, a.y + (b.y - a.y) * p)
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = (0.9f * alpha).coerceIn(0f, 1f)), Color.Transparent), pos, 12.dp.toPx()), 12.dp.toPx(), pos)
                drawCircle(Color.White.copy(alpha = alpha.coerceIn(0f, 1f)), 2.5.dp.toPx(), pos)
            }
            fun node(c: Offset, r: Float, icon: androidx.compose.ui.graphics.painter.Painter, active: Boolean, glow: Float = 0f) {
                if (glow > 0f) drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f * glow), Color.Transparent), c, r * 2.2f), r * 2.2f, c)
                drawCircle(Color(0xFF12161D), r, c)
                drawCircle((if (active) accent else dim).copy(alpha = if (active) 0.9f else 0.35f), r, c, style = Stroke(1.6.dp.toPx()))
                val s = r * 1.0f
                translate(c.x - s / 2, c.y - s / 2) { with(icon) { draw(androidx.compose.ui.geometry.Size(s, s), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (active) text else dim)) } }
            }
            line(you, hub, true)
            line(hub, out, true)
            packet(you, hub, 0f)
            packet(hub, out, 0.5f)
            val radius = h * 0.40f
            crew.forEachIndexed { i, s ->
                val angle = Math.toRadians((-160.0 + 140.0 * i / (crew.size - 1).coerceAtLeast(1))).toFloat()
                val pos = Offset(hub.x + cos(angle) * radius * 1.15f * on, hub.y + sin(angle) * radius * on)
                val ready = o.ready(s)
                if (on > 0.02f) {
                    line(hub, pos, ready, on)
                    if (ready) packet(hub, pos, i * 0.17f, on)
                    node(pos, 15.dp.toPx() * (0.6f + 0.4f * on), painters.getValue(s), ready)
                }
            }
            if (o.reviewing) {
                val rv = Offset(w * 0.72f, h * 0.84f)
                line(hub, rv, true, on); line(rv, out, true, on)
                packet(hub, rv, 0.3f, on)
                node(rv, 13.dp.toPx(), reviewIcon, true)
            }
            node(you, 17.dp.toPx(), youIcon, true)
            node(hub, 26.dp.toPx(), coordinatorIcon, true, glow = 0.6f + 0.4f * sin(flow * 2f * Math.PI.toFloat()))
            node(out, 17.dp.toPx(), answerIcon, true)
        }
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.orc_you), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.weight(1f))
            Text("Nexo", style = Quorvi.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text)
            Text(stringResource(R.string.orc_answer), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Text(
            if (!o.enabled) stringResource(R.string.orc_state_off) else if (o.delegates.isEmpty()) stringResource(R.string.orc_state_empty) else stringResource(R.string.orc_state_on, o.delegates.joinToString { s -> s.name }),
            style = Quorvi.type.caption, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.translate(x: Float, y: Float, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    drawContext.transform.translate(x, y)
    block()
    drawContext.transform.translate(-x, -y)
}

@Composable
fun ScreenBar(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Glyph(Glyphs.Back, 22.dp) }
        Text(title, style = Quorvi.type.title.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text, modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        action?.invoke()
    }
}

@Composable
fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(22.dp), GlassLevel.PANEL).padding(16.dp)) { content() }
}

@Composable
private fun Label(title: String, subtitle: String, glyph: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Glyph(glyph, 18.dp, Quorvi.colors.accent)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text)
            Text(subtitle, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
        }
    }
}

@Composable
fun ToggleLine(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).graphicsLayer { alpha = if (enabled) 1f else 0.5f }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = Quorvi.type.action, color = Quorvi.colors.text)
            subtitle?.let { Text(it, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary) }
        }
        QuorviSwitch(checked, if (enabled) onChange else null, enabled)
    }
}
