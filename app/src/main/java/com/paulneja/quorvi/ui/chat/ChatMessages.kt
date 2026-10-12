package com.paulneja.quorvi.ui.chat

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paulneja.quorvi.R
import com.paulneja.quorvi.agent.chat.ChatAttachment
import com.paulneja.quorvi.agent.chat.ChatMessageEntity
import com.paulneja.quorvi.agent.chat.ChatRole
import com.paulneja.quorvi.agent.chat.ChatStatus
import com.paulneja.quorvi.ops.RunState
import com.paulneja.quorvi.ops.StepEntity
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.ProgressLine
import com.paulneja.quorvi.ui.design.liquidGlass
import com.paulneja.quorvi.ui.operations.ApprovalCard
import com.paulneja.quorvi.ui.operations.PolicyViewModel
import com.paulneja.quorvi.ui.operations.runStateLabel
import java.io.File
import com.paulneja.quorvi.ui.chat.horizon.launchIn
import com.paulneja.quorvi.ui.chat.horizon.lightSweep
import com.paulneja.quorvi.ui.chat.horizon.orbLit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Modifier.entrance(animate: Boolean, fromBelow: Boolean = true): Modifier {
    val motion = Quorvi.motion
    val p = remember { Animatable(if (animate && motion.enabled) 0f else 1f) }
    LaunchedEffect(Unit) { if (p.value < 1f) p.animateTo(1f, spring(dampingRatio = (0.82f - motion.bounce * 0.3f).coerceIn(0.45f, 1f), stiffness = 260f * motion.speed * motion.speed)) }
    return graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * (if (fromBelow) 42.dp.toPx() else -24.dp.toPx())
        val s = 0.92f + 0.08f * v
        scaleX = s
        scaleY = s
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (fromBelow) 1f else 0f, 1f)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserBubble(m: ChatMessageEntity, animate: Boolean, onImage: (ChatAttachment) -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth().padding(start = 48.dp).launchIn(animate), horizontalAlignment = Alignment.End) {
        val files = m.files
        if (files.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                files.forEach { f -> if (f.image) ImageThumb(f, 132.dp) { onImage(f) } else FileChip(f) }
            }
        }
        if (m.text.isNotBlank()) {
            Text(
                highlightMentions(m.text, Quorvi.colors.accent), style = Quorvi.type.body.copy(fontSize = 15.5.sp, lineHeight = 22.sp), color = Quorvi.colors.text,
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp))
                    .background(Brush.linearGradient(listOf(Quorvi.colors.accent.copy(alpha = 0.30f), Quorvi.colors.accent.copy(alpha = 0.18f))))
                    .orbLit(20.dp, 0.7f)
                    .combinedClickable(onClick = {}, onLongClick = { clipboard.setText(AnnotatedString(m.text)) })
                    .padding(horizontal = 15.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
fun AssistantMessage(
    m: ChatMessageEntity,
    animate: Boolean,
    last: Boolean,
    policy: PolicyViewModel,
    runView: RunView?,
    onRetry: () -> Unit,
    onImage: (ChatAttachment) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val streaming = m.status == ChatStatus.STREAMING
    val sweepable = remember(m.id) { streaming || animate }
    Row(Modifier.fillMaxWidth().entrance(animate, fromBelow = false), verticalAlignment = Alignment.Top) {
        NexoAvatar(working = runView?.busy == true, size = 28.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)).lightSweep(if (streaming) null else m.status, sweepable && !streaming)) {
            if (runView != null && runView.steps.isNotEmpty()) ActivityStrip(runView)
            when {
                m.status == ChatStatus.ERROR -> ErrorCard(m.text, onRetry)
                m.text.isBlank() && streaming -> Thinking(runView)
                else -> {
                    MarkdownText(m.text)
                    if (streaming) Caret()
                }
            }
            if (m.files.isNotEmpty()) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    m.files.forEach { f -> if (f.image) GeneratedImage(f) { onImage(f) } else FileChip(f) }
                }
            }
            if (runView?.state == RunState.WAITING_USER) {
                Column(Modifier.padding(top = 10.dp).fillMaxWidth().liquidGlass(RoundedCornerShape(20.dp), GlassLevel.PANEL).padding(horizontal = 14.dp, vertical = 4.dp)) {
                    ApprovalCard(policy, runView.run!!.id)
                }
            }
            if (m.status == ChatStatus.STOPPED) Text(stringResource(R.string.chat_stopped), style = Quorvi.type.caption, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(top = 6.dp))
            AnimatedVisibility(!streaming && m.text.isNotBlank() && m.status != ChatStatus.ERROR, enter = fadeIn(tween(220)) + expandVertically()) {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    MiniAction(Glyphs.Copy) { clipboard.setText(AnnotatedString(m.text)) }
                    MiniAction(Glyphs.Share) {
                        runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, m.text), null)) }
                    }
                    if (last) MiniAction(Glyphs.Retry, onRetry)
                }
            }
        }
    }
}

@Composable
private fun MiniAction(glyph: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Glyph(glyph, 16.dp, Quorvi.colors.textSecondary) }
}

@Composable
private fun ErrorCard(detail: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Quorvi.colors.danger.copy(alpha = 0.12f)).padding(14.dp)) {
        Text(detail.ifBlank { stringResource(R.string.chat_error) }, style = Quorvi.type.body, color = Quorvi.colors.text)
        Text(
            stringResource(R.string.chat_retry), style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.danger,
            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onRetry).padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun Thinking(runView: RunView?) {
    val t = rememberInfiniteTransition(label = "dots")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "phase")
    Row(Modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val local = ((phase - i * 0.16f) % 1f + 1f) % 1f
            val lift = if (local < 0.5f) kotlin.math.sin(local * 2f * Math.PI).toFloat() else 0f
            val tint = com.paulneja.quorvi.ui.chat.horizon.LocalHorizon.current?.tint(Quorvi.colors.accent) ?: Quorvi.colors.accent
            Box(Modifier.padding(horizontal = 2.5.dp).graphicsLayer { translationY = -lift * 6.dp.toPx(); alpha = 0.45f + 0.55f * lift; val s = 0.8f + 0.45f * lift; scaleX = s; scaleY = s }.size(7.dp).clip(CircleShape).background(tint))
        }
        if (runView?.steps.isNullOrEmpty()) {
            Spacer(Modifier.width(10.dp))
            ShimmerText(stringResource(R.string.chat_thinking_short))
        }
    }
}

@Composable
fun ShimmerText(text: String) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "x")
    val base = Quorvi.colors.textSecondary
    AnimatedContent(text, transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) }, label = "label") { label ->
        Text(
            label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = Quorvi.type.action.copy(
                brush = Brush.linearGradient(
                    listOf(base, Quorvi.colors.text, base),
                    start = Offset(x * 400f - 200f, 0f), end = Offset(x * 400f + 200f, 0f),
                ),
            ),
        )
    }
}

@Composable
private fun Caret() {
    val t = rememberInfiniteTransition(label = "caret")
    val a by t.animateFloat(1f, 0.15f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "a")
    Box(Modifier.padding(top = 4.dp).graphicsLayer { alpha = a }.size(9.dp).clip(CircleShape).background(Quorvi.colors.accent))
}

@Composable
private fun ActivityStrip(view: RunView) {
    var open by remember(view.run?.id) { mutableStateOf(false) }
    val steps = view.steps
    val done = steps.count { it.state == "SUCCEEDED" }
    Column(Modifier.padding(bottom = 8.dp).fillMaxWidth()) {
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).clickable { open = !open }.background(Color.White.copy(alpha = 0.05f)).padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val current = view.current
            if (view.busy && current != null) {
                Spinner()
                Spacer(Modifier.width(8.dp))
                Box(Modifier.widthIn(max = 230.dp)) { ShimmerText(stepLabel(current)) }
            } else {
                Glyph(Glyphs.Sparkle, 14.dp, Quorvi.colors.accent)
                Spacer(Modifier.width(8.dp))
                Text(
                    pluralLabel(done, steps.size, steps.mapNotNull { it.kind.removePrefix("delegate:").takeIf { k -> it.kind.startsWith("delegate:") } }.distinct()),
                    style = Quorvi.type.caption.copy(fontWeight = FontWeight.Medium), color = Quorvi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = 0.6f), label = "turn")
            Glyph(Glyphs.Down, 14.dp, Quorvi.colors.textTertiary, Modifier.graphicsLayer { rotationZ = turn })
        }
        AnimatedVisibility(open, enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = 6.dp, top = 8.dp)) {
                steps.forEachIndexed { i, s -> StepRow(s, i == steps.lastIndex) }
            }
        }
    }
}

@Composable
private fun StepRow(s: StepEntity, last: Boolean) {
    var expanded by remember(s.id) { mutableStateOf(false) }
    val color = when (s.state) {
        "SUCCEEDED" -> Quorvi.colors.success
        "FAILED", "CANCELLED" -> Quorvi.colors.danger
        "RUNNING" -> Quorvi.colors.accent
        else -> Quorvi.colors.textTertiary
    }
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(16.dp)) {
            Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(color))
            if (!last) Box(Modifier.width(1.5.dp).weight(1f).background(Quorvi.colors.border))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(bottom = 10.dp).clickable(enabled = !s.output.isNullOrBlank()) { expanded = !expanded }) {
            Text(stepLabel(s), style = Quorvi.type.caption.copy(fontWeight = FontWeight.Medium), color = Quorvi.colors.text)
            if (s.kind.startsWith("delegate:")) Text(s.title, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = if (expanded) 20 else 2, overflow = TextOverflow.Ellipsis)
            else if (s.toolName != null && s.title != s.toolName) Text(s.title, style = Quorvi.type.mono.copy(fontSize = 12.sp), color = Quorvi.colors.textSecondary, maxLines = if (expanded) 4 else 1, overflow = TextOverflow.Ellipsis)
            if (expanded) s.output?.let {
                Text(it.take(3000), style = Quorvi.type.mono.copy(fontSize = 12.sp), color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF07090D)).padding(10.dp))
            }
        }
    }
}

@Composable
private fun Spinner() {
    val t = rememberInfiniteTransition(label = "spin")
    val r by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "r")
    val accent = Quorvi.colors.accent
    Canvas(Modifier.size(14.dp).graphicsLayer { rotationZ = r }) {
        drawArc(Brush.sweepGradient(listOf(Color.Transparent, accent)), 0f, 300f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

@Composable
fun stepLabel(s: StepEntity): String {
    val kind = s.kind
    if (kind.startsWith("delegate:")) return stringResource(
        when (kind.removePrefix("delegate:")) {
            "THINK" -> R.string.sp_act_think
            "PLAN" -> R.string.sp_act_plan
            "RESEARCH" -> R.string.sp_act_research
            "CODE" -> R.string.sp_act_code
            "WRITE" -> R.string.sp_act_write
            "IMAGE" -> R.string.sp_act_image
            else -> R.string.sp_act_review
        },
    )
    val tool = (s.toolName ?: s.title).replace('_', '.')
    return when {
        tool.startsWith("weather") -> stringResource(R.string.act_weather)
        tool == "repo.clone" -> stringResource(R.string.act_repo_clone)
        tool == "ws.list" || tool == "ws.read" -> stringResource(R.string.act_repo_read)
        tool == "ws.write" || tool == "ws.edit" -> stringResource(R.string.act_repo_edit)
        tool == "ws.run" -> stringResource(R.string.act_repo_run)
        tool == "ws.changes" -> stringResource(R.string.act_repo_changes)
        tool.startsWith("github.") -> stringResource(R.string.act_github)
        tool.startsWith("web.search") -> stringResource(R.string.act_web_search)
        tool.startsWith("web.fetch") -> stringResource(R.string.act_web_fetch)
        tool.startsWith("shell") || tool.startsWith("priv") || tool.startsWith("root") -> stringResource(R.string.act_shell)
        tool.startsWith("fs.") || tool.startsWith("artifact") -> stringResource(R.string.act_files)
        tool.startsWith("memory.save") -> stringResource(R.string.act_memory_save)
        tool.startsWith("memory") -> stringResource(R.string.act_memory)
        tool.startsWith("android") -> stringResource(R.string.act_phone)
        tool.startsWith("ssh") || tool.startsWith("remote") -> stringResource(R.string.act_server)
        tool.startsWith("ha.") -> stringResource(R.string.act_home)
        tool.startsWith("ci.") -> stringResource(R.string.act_ci)
        tool.startsWith("discord") -> stringResource(R.string.act_discord)
        else -> tool
    }
}

@Composable
private fun pluralLabel(done: Int, total: Int, roles: List<String>): String {
    val base = androidx.compose.ui.res.pluralStringResource(R.plurals.chat_steps_done, total, total)
    val names = roles.map { specialistName(it) }
    return if (names.isEmpty()) base else base + " · " + names.joinToString()
}

@Composable
fun specialistName(role: String): String = stringResource(
    when (role) {
        "THINK" -> R.string.sp_think
        "PLAN" -> R.string.sp_plan
        "RESEARCH" -> R.string.sp_research
        "CODE" -> R.string.sp_code
        "WRITE" -> R.string.sp_write
        "IMAGE" -> R.string.sp_image
        else -> R.string.sp_review
    },
)

@Composable
fun TaskCard(m: ChatMessageEntity, animate: Boolean, view: RunView?, onOpen: () -> Unit) {
    val state = view?.state
    val steps = view?.steps.orEmpty()
    val done = steps.count { it.state == "SUCCEEDED" || it.state == "SKIPPED" }
    Row(Modifier.fillMaxWidth().padding(start = 38.dp).entrance(animate, fromBelow = false)) {
        Column(
            Modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(20.dp), GlassLevel.PANEL).clickable(onClick = onOpen).padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Quorvi.colors.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { Glyph(Glyphs.Bolt, 16.dp, Quorvi.colors.accent) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.chat_task), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
                    Text(m.text, style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Glyph(Glyphs.Chevron, 16.dp, Quorvi.colors.textTertiary)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(runStateLabel(state), style = Quorvi.type.caption.copy(fontWeight = FontWeight.Medium), color = when (state) {
                    RunState.COMPLETED -> Quorvi.colors.success
                    RunState.FAILED -> Quorvi.colors.danger
                    else -> Quorvi.colors.accent
                })
                Spacer(Modifier.weight(1f))
                if (steps.isNotEmpty()) Text("$done/${steps.size}", style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
            }
            if (state != null && !state.terminal && steps.isNotEmpty()) {
                val f by androidx.compose.animation.core.animateFloatAsState(done.toFloat() / steps.size, spring(stiffness = 120f), label = "p")
                ProgressLine(f, modifier = Modifier.padding(top = 8.dp), height = 3.dp)
            }
        }
    }
}

@Composable
fun FileChip(f: ChatAttachment) {
    Row(
        Modifier.widthIn(max = 240.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.07f)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(Glyphs.File, 18.dp, Quorvi.colors.accent)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(f.name, style = Quorvi.type.caption.copy(fontWeight = FontWeight.Medium), color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(android.text.format.Formatter.formatShortFileSize(LocalContext.current, f.size), style = Quorvi.type.caption, color = Quorvi.colors.textTertiary)
        }
    }
}

@Composable
fun ImageThumb(f: ChatAttachment, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val bmp = rememberBitmap(f.path, 512)
    Box(Modifier.size(size).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f)).clickable(onClick = onClick)) {
        bmp?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
    }
}

@Composable
private fun GeneratedImage(f: ChatAttachment, onClick: () -> Unit) {
    val bmp = rememberBitmap(f.path, 1280)
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(bmp) { if (bmp != null) reveal.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 90f)) }
    val ratio = bmp?.let { it.width.toFloat() / it.height } ?: 1f
    Box(
        Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.5f, 2f)).clip(RoundedCornerShape(22.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Quorvi.colors.accent.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f))))
            .clickable(onClick = onClick),
    ) {
        bmp?.let {
            Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().graphicsLayer {
                alpha = reveal.value
                val s = 1.08f - 0.08f * reveal.value
                scaleX = s; scaleY = s
                if (android.os.Build.VERSION.SDK_INT >= 31 && reveal.value < 0.99f) {
                    val r = (1f - reveal.value) * 40f
                    renderEffect = androidx.compose.ui.graphics.BlurEffect(r, r, androidx.compose.ui.graphics.TileMode.Clamp)
                }
            })
        }
    }
}

@Composable
fun rememberBitmap(path: String, maxSide: Int): ImageBitmap? = produceState<ImageBitmap?>(null, path, maxSide) {
    value = withContext(Dispatchers.IO) { decode(File(path), maxSide)?.asImageBitmap() }
}.value

@Composable
fun rememberUriBitmap(uri: Uri, maxSide: Int): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }?.asImageBitmap()
            }.getOrNull()
        }
    }.value
}

private fun decode(file: File, maxSide: Int): Bitmap? {
    if (!file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
fun NexoAvatar(working: Boolean, size: androidx.compose.ui.unit.Dp) {
    com.paulneja.quorvi.ui.chat.orb.NexoOrb(
        size, if (LocalUltra.current) com.paulneja.quorvi.ui.chat.orb.OrbMode.ULTRA else com.paulneja.quorvi.ui.chat.orb.OrbMode.CHAT,
        if (working) com.paulneja.quorvi.ui.chat.orb.OrbState.THINKING else com.paulneja.quorvi.ui.chat.orb.OrbState.IDLE,
        animate = working,
    )
}
