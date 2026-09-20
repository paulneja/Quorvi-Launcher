package com.paulneja.quorvi.labs

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

private val CardShape = RoundedCornerShape(24.dp)

private data class Card(val id: Int, val title: String, val color: Color)

private val cards = listOf(
    Card(1, "Build #482", Color(0xFF2E6BFF)),
    Card(2, "Server eu-1", Color(0xFF13A07A)),
    Card(3, "Run: report", Color(0xFFB4541A)),
    Card(4, "Notes", Color(0xFF7A4BD6)),
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MotionLab() {
    var selected by remember { mutableStateOf<Int?>(null) }
    val counters = remember { mutableStateMapOf<Int, Int>() }
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "motion-lab",
        ) { current ->
            if (current == null) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(cards, key = { it.id }) { card ->
                        Column(
                            Modifier
                                .sharedBounds(
                                    rememberSharedContentState("card-${card.id}"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                    clipInOverlayDuringTransition = OverlayClip(CardShape),
                                )
                                .clip(CardShape)
                                .background(card.color)
                                .clickable { selected = card.id }
                                .height(120.dp)
                                .padding(16.dp),
                        ) {
                            Text(card.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text("taps: ${counters[card.id] ?: 0}", color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            } else {
                val card = cards.first { it.id == current }
                val scope = rememberCoroutineScope()
                val back = remember { Animatable(0f) }
                PredictiveBackHandler { progress ->
                    try {
                        progress.collect { back.snapTo(it.progress) }
                        selected = null
                    } catch (e: CancellationException) {
                        scope.launch { back.animateTo(0f, spring()) }
                        throw e
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .graphicsLayer {
                            val s = 1f - 0.12f * back.value
                            scaleX = s
                            scaleY = s
                        }
                        .sharedBounds(
                                    rememberSharedContentState("card-${card.id}"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                    clipInOverlayDuringTransition = OverlayClip(CardShape),
                                )
                        .clip(CardShape)
                        .background(card.color)
                        .padding(24.dp),
                ) {
                    Column(Modifier.fillMaxWidth().skipToLookaheadSize()) {
                        Text(card.title, color = Color.White, style = MaterialTheme.typography.headlineMedium)
                        Text("State survives expand and collapse.", color = Color.White.copy(alpha = 0.8f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("taps: ${counters[card.id] ?: 0}", color = Color.White, style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { counters[card.id] = (counters[card.id] ?: 0) + 1 }) { Text("+1", color = Color.White) }
                        }
                        TextButton(onClick = { selected = null }) { Text("Collapse", color = Color.White) }
                    }
                }
            }
        }
    }
}
