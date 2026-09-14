package com.paulneja.quorvi.ui.canvas

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulneja.quorvi.R
import com.paulneja.quorvi.launcher.apps.AppEntry
import com.paulneja.quorvi.launcher.apps.ProfileState
import com.paulneja.quorvi.launcher.home.CanvasNode
import com.paulneja.quorvi.launcher.home.CellRect
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.LocalGlassIntensity
import com.paulneja.quorvi.ui.design.LocalWallpaperTint
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.glass
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import kotlinx.coroutines.launch

private enum class DrawerTab { PERSONAL, WORK }

@Composable
fun AppDrawer(
    viewModel: CanvasViewModel,
    drawer: DrawerState,
    columns: Int,
    focusSearch: Boolean,
    onSearchFocused: () -> Unit,
    onLongPressApp: (AppEntry, PointerId, Offset, Rect) -> Unit,
    topApps: List<AppEntry> = emptyList(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val content by viewModel.drawer.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val closed by remember { derivedStateOf { drawer.closed } }
    val opened by remember { derivedStateOf { drawer.progress == 1f } }
    val tint = LocalWallpaperTint.current
    val geometry = remember { CanvasGeometry() }
    var tab by rememberSaveable { mutableStateOf(DrawerTab.PERSONAL) }
    var armedTop by remember { mutableStateOf<com.paulneja.quorvi.launcher.apps.AppKey?>(null) }

    LaunchedEffect(closed) {
        if (closed) {
            keyboard?.hide()
            focus.clearFocus()
            viewModel.setQuery("")
            tab = DrawerTab.PERSONAL
            armedTop = null
            list.scrollToItem(0)
        }
    }
    LaunchedEffect(opened) {
        if (opened) list.scrollToItem(0)
    }
    LaunchedEffect(opened, focusSearch) {
        if (opened && focusSearch) {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
            onSearchFocused()
        }
    }

    val haptics = com.paulneja.quorvi.ui.design.rememberHaptics()
    val launchApp = rememberAppLauncher(viewModel)
    val badges = LocalBadges.current
    val appRow: @Composable (AppEntry) -> Unit = { app ->
        val node = remember(app.key) { CanvasNode.App(-app.key.hashCode().toLong() - 1, 0, 0, CellRect(0, 0), false, app) }
        NodeHost(
            node = node,
            geometry = geometry,
            hidden = false,
            trackBounds = false,
            onLongPress = { id, at, bounds -> onLongPressApp(app, id, at, bounds) },
            onClick = null,
            onClickAt = { bounds -> launchApp(app, Rect(bounds.left + 20f, bounds.top, bounds.left + 20f + bounds.height, bounds.bottom)) },
            modifier = Modifier.fillMaxWidth().then(if (app.paused) Modifier.alpha(0.45f) else Modifier),
        ) {
            Row(Modifier.fillMaxWidth().height(58.dp).padding(start = 20.dp, end = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AppIcon(app, 42.dp)
                    if (app.key.packageName in badges) Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Quorvi.colors.danger))
                }
                Spacer(Modifier.width(16.dp))
                Text(app.label, style = Quorvi.type.body.copy(fontSize = androidx.compose.ui.unit.TextUnit(15.5f, androidx.compose.ui.unit.TextUnitType.Sp)), color = Quorvi.colors.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, ((1f - drawer.progress) * drawer.heightPx).roundToInt()) }

            .then(if (drawer.open) Modifier.pointerInput(Unit) { detectTapGestures { } } else Modifier),
    ) {
        val scrim = remember(tint) { tint.copy(alpha = 0.10f).compositeOver(Color(0xFF0B0C10)).copy(alpha = 0.72f) }
        Box(Modifier.matchParentSize().drawBehind { drawRect(scrim) })
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .glass(Quorvi.shapes.pill, GlassLevel.CHROME, tint, LocalGlassIntensity.current)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(rememberVectorPainter(Glyphs.Search), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.8f)))
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    singleLine = true,
                    textStyle = Quorvi.type.body.copy(color = Color.White),
                    cursorBrush = SolidColor(Quorvi.colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { content.personal.firstOrNull()?.let(viewModel::launch) }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text(stringResource(R.string.search_apps), color = Color.White.copy(alpha = 0.5f), style = Quorvi.type.body)
                        inner()
                    },
                )
            }
            if (!content.searching && content.workState != null) {
                TabRow(tab, onSelect = { tab = it })
            }
            if (content.searching && content.personal.isEmpty()) {
                Text(stringResource(R.string.no_results, query), color = Quorvi.colors.textSecondary, style = Quorvi.type.body, modifier = Modifier.padding(24.dp))
            }
            val showWork = !content.searching && tab == DrawerTab.WORK
            val apps = remember(content, showWork) { com.paulneja.quorvi.launcher.apps.DrawerIndex.order(if (showWork) content.work else content.personal) { it.label } }
            val showTop = !content.searching && !showWork && topApps.isNotEmpty()
            val sections = remember(apps) { com.paulneja.quorvi.launcher.apps.DrawerIndex.sections(apps.map { it.label }) }
            val headerAt = remember(sections, showTop, showWork) { com.paulneja.quorvi.launcher.apps.DrawerIndex.headerPositions(sections, (if (showTop) 1 else 0) + (if (showWork) 1 else 0)) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var railLetter by remember { mutableStateOf<Char?>(null) }
            Box(Modifier.weight(1f)) {
                androidx.compose.foundation.lazy.LazyColumn(
                    state = list,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize().nestedScroll(drawer.nestedScroll).windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    if (showTop) item(key = "top", contentType = "top") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text(stringResource(R.string.drawer_most_used).uppercase(), style = Quorvi.type.section, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                            Row(Modifier.fillMaxWidth().glass(Quorvi.shapes.panel, GlassLevel.CHROME, tint, LocalGlassIntensity.current).padding(vertical = 10.dp)) {
                                topApps.take(5).forEach { app -> androidx.compose.runtime.key(app.key) {
                                    var cellBounds by remember { mutableStateOf(Rect.Zero) }
                                    val isArmed = armedTop == app.key
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        Column(
                                            Modifier.fillMaxWidth().onGloballyPositioned { cellBounds = it.boundsInRoot() }.clip(Quorvi.shapes.control)
                                                .combinedClickable(
                                                    onClick = { if (armedTop != null) armedTop = null else launchApp(app, cellBounds) },
                                                    onLongClick = { haptics.pickup(); armedTop = if (isArmed) null else app.key },
                                                )
                                                .alpha(if (armedTop != null && !isArmed) 0.45f else 1f),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            AppIcon(app, 46.dp)
                                            Spacer(Modifier.height(5.dp))
                                            Text(app.label, style = Quorvi.type.label, color = Quorvi.colors.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
                                        }
                                        androidx.compose.animation.AnimatedVisibility(
                                            isArmed,
                                            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(140)) + androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(140), initialScale = 0.9f),
                                            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)),
                                            modifier = Modifier.matchParentSize(),
                                        ) {
                                            Column(
                                                Modifier.fillMaxSize().padding(horizontal = 2.dp).clip(Quorvi.shapes.control).background(Color(0xFF25252A))
                                                    .clickable { haptics.confirm(); armedTop = null; viewModel.setSuggested(app, false) }
                                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                            ) {
                                                Image(rememberVectorPainter(Glyphs.Hide), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(Color.White))
                                                Spacer(Modifier.height(4.dp))
                                                Text(stringResource(R.string.menu_unsuggest), style = Quorvi.type.label, color = Color.White, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                            }
                                        }
                                    }
                                } }
                            }
                            androidx.compose.animation.AnimatedVisibility(armedTop != null) {
                                Text(stringResource(R.string.drawer_unsuggest_hint), style = Quorvi.type.label, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                            }
                        }
                    }
                    if (showWork) content.workState?.let { state ->
                        item(key = "work-toggle") {
                            ProfileHeader(
                                title = stringResource(if (state.paused) R.string.work_paused_hint else R.string.tab_work),
                                action = stringResource(if (state.paused) R.string.work_resume else R.string.work_pause),
                                onAction = { viewModel.setProfilePaused(state, !state.paused) },
                            )
                        }
                    }
                    sections.forEach { section ->
                        item(key = "h:" + section.letter + (if (showWork) "w" else ""), contentType = "header") {
                            Text(
                                section.letter.toString(),
                                style = Quorvi.type.title,
                                color = Quorvi.colors.accent,
                                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 10.dp, bottom = 4.dp),
                            )
                        }
                        items(apps.subList(section.start, section.start + section.size), key = { (if (showWork) "w" else "") + it.key.encode() }, contentType = { "app" }) { appRow(it) }
                    }
                    if (!content.searching && !showWork) content.privateState?.let { state ->
                        item(key = "private-header") {
                            ProfileHeader(
                                title = stringResource(R.string.private_space) + if (state.paused) " · " + stringResource(R.string.private_locked) else "",
                                action = stringResource(if (state.paused) R.string.private_unlock else R.string.private_lock),
                                glyph = if (state.paused) Glyphs.Lock else Glyphs.Unlock,
                                onAction = { viewModel.setProfilePaused(state, !state.paused) },
                                modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
                            )
                        }
                        if (!state.paused) items(content.private, key = { "p" + it.key.encode() }, contentType = { "app" }) { appRow(it) }
                    }
                }
                if (!content.searching && sections.size > 1) {
                    val letters = sections.map { it.letter }
                    LetterRail(
                        letters = letters,
                        active = railLetter,
                        onLetter = { letter ->
                            if (letter != railLetter) {
                                railLetter = letter
                                haptics.snap()
                                headerAt[letter]?.let { scope.launch { list.scrollToItem(it) } }
                            }
                        },
                        onRelease = { railLetter = null },
                        modifier = Modifier.align(Alignment.CenterEnd).windowInsetsPadding(WindowInsets.navigationBars),
                    )
                }
                railLetter?.let { letter ->
                    Box(
                        Modifier.align(Alignment.Center).size(84.dp).glass(Quorvi.shapes.panel, GlassLevel.OVERLAY, tint, LocalGlassIntensity.current),
                        contentAlignment = Alignment.Center,
                    ) { Text(letter.toString(), style = Quorvi.type.headline.copy(fontSize = androidx.compose.ui.unit.TextUnit(40f, androidx.compose.ui.unit.TextUnitType.Sp)), color = Quorvi.colors.text) }
                }
            }
        }
    }
}

@Composable
private fun LetterRail(letters: List<Char>, active: Char?, onLetter: (Char) -> Unit, onRelease: () -> Unit, modifier: Modifier = Modifier) {
    var height by remember { mutableStateOf(1) }
    Column(
        modifier
            .padding(end = 6.dp)
            .width(28.dp)
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .pointerInput(letters) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    com.paulneja.quorvi.launcher.apps.DrawerIndex.letterAt(down.position.y / height, letters)?.let(onLetter)
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        com.paulneja.quorvi.launcher.apps.DrawerIndex.letterAt(change.position.y / height, letters)?.let(onLetter)
                        change.consume()
                    } while (change.pressed)
                    onRelease()
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            Text(
                letter.toString(),
                style = Quorvi.type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = if (letter == active) Quorvi.colors.accent else Quorvi.colors.textSecondary,
                modifier = Modifier.padding(vertical = 1.5.dp),
            )
        }
    }
}

@Composable
private fun TabRow(selected: DrawerTab, onSelect: (DrawerTab) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .glass(Quorvi.shapes.control, GlassLevel.CHROME, LocalWallpaperTint.current, LocalGlassIntensity.current)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DrawerTab.entries.forEach { tab ->
            val active = tab == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(Quorvi.shapes.control)
                    .background(if (active) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (tab == DrawerTab.PERSONAL) R.string.tab_personal else R.string.tab_work),
                    style = Quorvi.type.action,
                    color = if (active) Quorvi.colors.text else Quorvi.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    title: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .glass(Quorvi.shapes.control, GlassLevel.CHROME, LocalWallpaperTint.current, LocalGlassIntensity.current)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Image(rememberVectorPainter(glyph), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(Quorvi.colors.text))
            Spacer(Modifier.width(10.dp))
        }
        Text(title, style = Quorvi.type.action, color = Quorvi.colors.text, modifier = Modifier.weight(1f))
        Text(
            action,
            style = Quorvi.type.action,
            color = Quorvi.colors.accent,
            modifier = Modifier.clip(Quorvi.shapes.control).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
