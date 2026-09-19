package com.paulneja.quorvi.ui.bento

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paulneja.quorvi.launcher.live.NowPlaying
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Quorvi
import kotlin.math.sin
import kotlinx.coroutines.launch

private fun dominant(art: ImageBitmap?): Color? = art?.let {
    runCatching {
        val px = android.graphics.Bitmap.createScaledBitmap(it.asAndroidBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888, false), 1, 1, true).getPixel(0, 0)
        val c = Color(px)

        val lift = 0.35f
        Color(c.red + (1f - c.red) * lift * 0.4f, c.green + (1f - c.green) * lift * 0.4f, c.blue + (1f - c.blue) * lift * 0.4f)
    }.getOrNull()
}

@Composable
fun MediaWidget(now: NowPlaying, onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, onSeek: (Long) -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current
    val tint = remember(now.art) { dominant(now.art) } ?: Quorvi.colors.accent
    val appName = remember(now.packageName) {
        runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(now.packageName, 0)).toString() }.getOrNull()
    }
    val motion = rememberInfiniteTransition(label = "media")
    val zoom by motion.animateFloat(1.15f, 1.35f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse), label = "zoom")
    val drift by motion.animateFloat(-12f, 12f, infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    val playing by animateFloatAsState(if (now.playing) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 120f), label = "playing")
    BoxWithConstraints(
        Modifier.fillMaxSize().padding(5.dp).clip(Quorvi.shapes.panel).background(Color(0xFF0B0E13)).clickable(onClick = onOpen),
    ) {
        val tall = maxHeight > 190.dp
        now.art?.let { art ->
            Image(
                art, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val z = zoom + 0.05f * playing
                    scaleX = z; scaleY = z
                    translationX = drift * density
                    alpha = 0.55f
                    renderEffect = BlurEffect(38f, 38f, TileMode.Decal)
                },
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.78f)))))
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(now.art to now.title, transitionSpec = { trackChange() }, label = "art") { (art, _) ->
                    Cover(art, if (tall) 60.dp else 46.dp, playing)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    AnimatedContent(now.title to now.artist, transitionSpec = { trackChange() }, label = "title") { (title, artist) ->
                        Column {
                            @OptIn(ExperimentalFoundationApi::class)
                            Text(title, style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp), color = Color.White, maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000))
                            Text(listOfNotNull(artist, appName).joinToString(" · "), style = Quorvi.type.caption, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Equalizer(playing, tint, Modifier.size(width = 26.dp, height = 22.dp))
            }
            if (tall) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(8.dp))
            if (now.durationMs > 0) Progress(now, tint, onSeek)
            Spacer(Modifier.height(if (tall) 8.dp else 2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                SkipButton(Glyphs.Previous, -1f, onPrevious)
                PlayButton(now.playing, tint, if (tall) 50.dp else 42.dp, onPlayPause)
                SkipButton(Glyphs.Next, 1f, onNext)
            }
        }
    }
}

private fun androidx.compose.animation.AnimatedContentTransitionScope<*>.trackChange(): ContentTransform =
    (slideInHorizontally(spring(dampingRatio = 0.75f, stiffness = 260f)) { it / 3 } + fadeIn(tween(260)) + scaleIn(initialScale = 0.9f)) togetherWith
        (slideOutHorizontally(tween(220)) { -it / 3 } + fadeOut(tween(180)) + scaleOut(targetScale = 0.92f))

@Composable
private fun Cover(art: ImageBitmap?, size: androidx.compose.ui.unit.Dp, playing: Float) {
    val t = rememberInfiniteTransition(label = "cover")
    val beat by t.animateFloat(0f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "beat")
    val sway by t.animateFloat(-3f, 3f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "sway")
    Box(
        Modifier.size(size).graphicsLayer {
            val s = 1f + 0.035f * beat * playing
            scaleX = s; scaleY = s
            rotationZ = sway * playing
            shadowElevation = 18f * playing
            shape = RoundedCornerShape(14.dp)
            clip = true
        }.background(Color.White.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Glyph(Glyphs.Play, size * 0.4f, Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun Equalizer(playing: Float, tint: Color, modifier: Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { val start = withFrameMillis { it }; while (true) withFrameMillis { time = (it - start) / 1000f } }
    Canvas(modifier) {
        val bars = 5
        val w = size.width / (bars * 2 - 1)
        repeat(bars) { i ->
            val wave = (sin(time * (5.5f + i * 1.7f) + i * 1.3f) * 0.5f + 0.5f) * (sin(time * 2.1f + i) * 0.25f + 0.75f)
            val h = size.height * (0.18f + 0.82f * wave * playing)
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.White, tint), startY = size.height - h, endY = size.height),
                topLeft = Offset(i * 2 * w, size.height - h), size = Size(w, h), cornerRadius = CornerRadius(w / 2, w / 2),
            )
        }
    }
}

@Composable
private fun Progress(now: NowPlaying, tint: Color, onSeek: (Long) -> Unit) {
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(now.playing, now.updatedAt) { while (now.playing) withFrameMillis { tick = SystemClock.elapsedRealtime() } }
    var dragging by remember { mutableFloatStateOf(-1f) }
    val position = now.positionAt(tick)
    val fraction = if (dragging >= 0f) dragging else (position.toFloat() / now.durationMs).coerceIn(0f, 1f)
    val thumb by animateFloatAsState(if (dragging >= 0f) 1f else 0f, spring(dampingRatio = 0.5f), label = "thumb")
    Column {
        Box(
            Modifier.fillMaxWidth().height(22.dp)
                .pointerInput(now.durationMs) {
                    detectTapGestures { o -> onSeek((o.x / size.width * now.durationMs).toLong()) }
                }
                .pointerInput(now.durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragging = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { onSeek((dragging * now.durationMs).toLong()); dragging = -1f },
                        onDragCancel = { dragging = -1f },
                    ) { change, _ -> change.consume(); dragging = (change.position.x / size.width).coerceIn(0f, 1f) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                val y = size.height / 2
                val h = (4f + 3f * thumb) * density
                drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h / 2))
                drawRoundRect(Brush.horizontalGradient(listOf(tint, Color.White)), Offset(0f, y - h / 2), Size(size.width * fraction, h), CornerRadius(h / 2))
                val x = size.width * fraction
                drawCircle(tint.copy(alpha = 0.35f * (0.4f + thumb)), (9f + 7f * thumb) * density, Offset(x, y))
                drawCircle(Color.White, (4f + 3f * thumb) * density, Offset(x, y))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(mmss(if (dragging >= 0f) (dragging * now.durationMs).toLong() else position), style = Quorvi.type.mono.copy(fontSize = 10.sp), color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            Text("-" + mmss((now.durationMs - position).coerceAtLeast(0)), style = Quorvi.type.mono.copy(fontSize = 10.sp), color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun PlayButton(playing: Boolean, tint: Color, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.86f else 1f, spring(dampingRatio = 0.4f, stiffness = 700f), label = "press")
    val rings = rememberInfiniteTransition(label = "rings")
    val ring by rings.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "ring")

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (playing) Canvas(Modifier.requiredSize(size * 1.6f)) {
            listOf(ring, (ring + 0.5f) % 1f).forEach { p ->
                drawCircle(tint.copy(alpha = (1f - p) * 0.45f), radius = size.toPx() / 2 * (1f + 0.5f * p), style = Stroke(2f * density))
            }
        }
        Box(
            Modifier.size(size).graphicsLayer { scaleX = press; scaleY = press }.clip(CircleShape).background(Color.White)
                .clickable(interactionSource = source, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(playing, transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.45f, stiffness = 500f), initialScale = 0.4f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.4f) + fadeOut(tween(120)))
            }, label = "icon") { p ->
                Glyph(if (p) Glyphs.Pause else Glyphs.Play, size * 0.42f, Color(0xFF0E1116))
            }
        }
    }
}

@Composable
private fun SkipButton(glyph: ImageVector, direction: Float, onClick: () -> Unit) {
    val nudge = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable {
            onClick()
            scope.launch {
                nudge.snapTo(0f)
                nudge.animateTo(1f, tween(110))
                nudge.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 400f))
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Glyph(glyph, 24.dp, Color.White, Modifier.graphicsLayer { translationX = nudge.value * direction * 10.dp.toPx(); alpha = 1f - 0.3f * nudge.value })
    }
}

private fun mmss(ms: Long): String = "%d:%02d".format(ms / 60_000, (ms / 1000) % 60)
