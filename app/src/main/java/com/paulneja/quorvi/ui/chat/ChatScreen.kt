package com.paulneja.quorvi.ui.chat

import com.paulneja.quorvi.ui.design.hideKeyboard
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paulneja.quorvi.R
import com.paulneja.quorvi.agent.chat.ChatAge
import com.paulneja.quorvi.agent.chat.ChatAttachment
import com.paulneja.quorvi.agent.chat.ChatEntity
import com.paulneja.quorvi.agent.chat.ChatLogic
import com.paulneja.quorvi.agent.chat.ChatRole
import com.paulneja.quorvi.ops.RunState
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Haptics
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.liquidGlass
import com.paulneja.quorvi.ui.design.liquidPress
import com.paulneja.quorvi.ui.operations.OperationsActivity
import com.paulneja.quorvi.ui.operations.PolicyViewModel
import kotlinx.coroutines.launch
import com.paulneja.quorvi.ui.chat.horizon.horizonAnchor
import com.paulneja.quorvi.ui.chat.horizon.horizonHost
import com.paulneja.quorvi.ui.chat.horizon.orbLit
import com.paulneja.quorvi.ui.chat.horizon.orbTouch

private enum class ChatPage { CHAT, ORCHESTRA, MEMORY, ULTRA, MODELS }

@Composable
fun ChatApp(vm: ChatViewModel, policy: PolicyViewModel, initialPrompt: String?, onClose: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(ChatPage.CHAT) }
    var viewer by remember { mutableStateOf<ChatAttachment?>(null) }
    var picker by remember { mutableStateOf(false) }
    var reasoning by remember { mutableStateOf(false) }
    var voiceMode by rememberSaveable { mutableStateOf(false) }
    val voiceOn by vm.voiceEnabled.collectAsState()
    val voice = androidx.hilt.navigation.compose.hiltViewModel<com.paulneja.quorvi.voice.AssistantViewModel>(key = "chat-voice")
    val voiceContext = LocalContext.current
    val voiceFocus = androidx.compose.ui.platform.LocalFocusManager.current
    val voiceKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val askMic = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) voice.start(vm.chatId.value) else voiceMode = false
    }
    fun openVoice() {
        voiceFocus.clearFocus(force = true)
        voiceKeyboard?.hide()
        voiceMode = true
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(voiceContext, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) voice.start(vm.chatId.value) else askMic.launch(android.Manifest.permission.RECORD_AUDIO)
    }
    val colors = Quorvi.colors
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.sunken, colors.content)))) {
        AnimatedContent(
            page,
            transitionSpec = {
                val forward = targetState != ChatPage.CHAT
                (slideInHorizontally(spring(dampingRatio = 0.86f, stiffness = 420f)) { if (forward) it else -it / 3 } + fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally(spring(dampingRatio = 0.86f, stiffness = 420f)) { if (forward) -it / 3 else it } + fadeOut(tween(160)))
            },
            label = "page",
        ) { p ->
            when (p) {
                ChatPage.CHAT -> ChatHome(vm, policy, initialPrompt, onClose, onOrchestra = { page = ChatPage.ORCHESTRA }, onMemory = { page = ChatPage.MEMORY }, onUltra = { page = ChatPage.ULTRA }, onImage = { viewer = it }, onPickRepo = { picker = true }, onReasoning = { reasoning = true }, onVoice = if (voiceOn) ::openVoice else null)
                ChatPage.ORCHESTRA -> OrchestraScreen(vm, onBack = { page = ChatPage.CHAT }, onModels = { page = ChatPage.MODELS })
                ChatPage.MODELS -> ModelsScreen(vm, onBack = { page = ChatPage.ORCHESTRA })
                ChatPage.MEMORY -> MemoryScreen(vm, onBack = { page = ChatPage.CHAT })
                ChatPage.ULTRA -> UltraScreen(vm, onBack = { page = ChatPage.CHAT })
            }
        }
        AnimatedVisibility(picker, enter = fadeIn(tween(160)) + slideInVertically { it / 3 }, exit = fadeOut(tween(140)) + slideOutVertically { it / 3 }) { RepoPicker(vm) { picker = false } }
        AnimatedVisibility(reasoning, enter = fadeIn(tween(160)) + slideInVertically { it / 3 }, exit = fadeOut(tween(140)) + slideOutVertically { it / 3 }) { ReasoningSheet(vm) { reasoning = false } }
        AnimatedVisibility(voiceMode, enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.94f), exit = fadeOut(tween(180)) + scaleOut(targetScale = 1.04f)) {
            androidx.activity.compose.BackHandler { voice.end(); voiceMode = false }
            com.paulneja.quorvi.voice.VoiceModeOverlay(voice, ultra = vm.chat.collectAsState().value?.ultra == true, onMic = { voice.listen() }, onClose = {
                voiceMode = false

                voice.ui.value.chatId?.let { if (vm.chatId.value != it) vm.open(it) }
            })
        }
        AnimatedVisibility(viewer != null, enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.9f), exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.94f)) {
            viewer?.let { ImageViewer(it) { viewer = null } }
        }
    }
}

@Composable
private fun ChatHome(
    vm: ChatViewModel,
    policy: PolicyViewModel,
    initialPrompt: String?,
    onClose: () -> Unit,
    onOrchestra: () -> Unit,
    onMemory: () -> Unit,
    onUltra: () -> Unit,
    onImage: (ChatAttachment) -> Unit,
    onPickRepo: () -> Unit,
    onReasoning: () -> Unit,
    onVoice: (() -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current
    val drawerWidth = 304.dp
    val widthPx = with(density) { drawerWidth.toPx() }
    val drawer = remember { Animatable(0f) }
    val haptics = com.paulneja.quorvi.ui.design.rememberHaptics()
    fun setDrawer(open: Boolean) = scope.launch {
        if (open && drawer.value < 0.5f) haptics.snap()
        drawer.animateTo(if (open) 1f else 0f, spring(dampingRatio = 0.82f, stiffness = 340f))
    }
    BackHandler(drawer.value > 0.01f) { setDrawer(false) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(Unit) {
        snapshotFlow { drawer.value > 0.02f }.distinctUntilChanged().collect { opening ->
            if (opening) { focus.clearFocus(force = true); view.hideKeyboard() }
        }
    }

    val startMode by vm.pendingMode.collectAsState()
    val horizon = com.paulneja.quorvi.ui.chat.horizon.rememberHorizon(startMode == com.paulneja.quorvi.agent.chat.ChatMode.ULTRA)
    val busyNow = vm.live.collectAsState().value?.busy == true
    Box(Modifier.fillMaxSize()) {
        ChatDrawer(
            vm = vm,
            progress = { drawer.value.coerceIn(0f, 1f) },
            modifier = Modifier.width(drawerWidth).fillMaxHeight(),
            onPick = { vm.open(it); setDrawer(false) },
            onNew = { vm.open(null); setDrawer(false) },
            onOrchestra = { setDrawer(false); onOrchestra() },
            onMemory = { setDrawer(false); onMemory() },
            onUltra = { setDrawer(false); onUltra() },
        )
        val dragState = rememberDraggableState { delta -> scope.launch { drawer.snapTo((drawer.value + delta / widthPx).coerceIn(0f, 1f)) } }
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val raw = drawer.value
                    val p = raw.coerceIn(0f, 1f)
                    translationX = raw.coerceAtLeast(-0.02f) * widthPx
                    val s = 1f - 0.07f * p
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    shape = RoundedCornerShape((30 * p).dp)
                    clip = p > 0f
                    shadowElevation = 24f * p
                }
                .draggable(dragState, Orientation.Horizontal, onDragStopped = { velocity -> setDrawer(velocity > 600f || (velocity > -600f && drawer.value > 0.4f)) }),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(com.paulneja.quorvi.ui.chat.horizon.LocalHorizon provides horizon) {
                com.paulneja.quorvi.ui.chat.horizon.NexoField(horizon, busyNow, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().horizonHost(horizon)) {
                    ChatMain(vm, policy, horizon, initialPrompt, onMenu = { setDrawer(true) }, onClose = onClose, onImage = onImage, onPickRepo = onPickRepo, onReasoning = onReasoning, onVoice = onVoice)
                }
                com.paulneja.quorvi.ui.chat.horizon.HorizonOrb(horizon)
            }
            if (drawer.value > 0f) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f * drawer.value.coerceIn(0f, 1f)))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { setDrawer(false) },
                )
            }
        }
    }
}

@Composable
private fun ChatMain(vm: ChatViewModel, policy: PolicyViewModel, horizon: com.paulneja.quorvi.ui.chat.horizon.HorizonState, initialPrompt: String?, onMenu: () -> Unit, onClose: () -> Unit, onImage: (ChatAttachment) -> Unit, onPickRepo: () -> Unit, onReasoning: () -> Unit, onVoice: (() -> Unit)?) {
    val pendingMode by vm.pendingMode.collectAsState()
    val pendingRepo by vm.pendingRepo.collectAsState()
    val chat by vm.chat.collectAsState()
    val messages by vm.messages.collectAsState()
    val live by vm.live.collectAsState()
    val providers by vm.providerList.collectAsState()
    val orchestra by vm.orchestra.collectAsState()
    val chatId by vm.chatId.collectAsState()
    val context = LocalContext.current
    var draft by rememberSaveable(chatId) { mutableStateOf("") }
    val seed by vm.draftSeed.collectAsState()
    LaunchedEffect(seed) { seed?.let { draft = it; vm.consumeDraft() } }
    val files = remember(chatId) { mutableStateListOf<Uri>() }
    var prompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialPrompt) {
        if (!prompted && !initialPrompt.isNullOrBlank()) { prompted = true; vm.send(initialPrompt, emptyList()) }
    }
    val busy = live?.busy == true
    val waitingQuestion = live?.state == RunState.WAITING_USER
    val motion = Quorvi.motion.enabled
    val switchScope = rememberCoroutineScope()
    val switchHaptics = com.paulneja.quorvi.ui.design.rememberHaptics()
    var guided by remember { mutableStateOf(false) }
    val wantUltra = if (messages?.isEmpty() != false) pendingMode == com.paulneja.quorvi.agent.chat.ChatMode.ULTRA else chat?.ultra == true
    LaunchedEffect(wantUltra) { if (!guided) horizon.snap(wantUltra) }
    val shownUltra by remember(horizon) { derivedStateOf { horizon.phase >= 0.5f } }
    fun switchMode(mode: String) {
        if (mode == pendingMode) return
        guided = true
        vm.setPendingMode(mode)
        switchScope.launch {
            try { horizon.go(mode == com.paulneja.quorvi.agent.chat.ChatMode.ULTRA, motion) { switchHaptics.pickup() } } finally { guided = false }
        }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(Glyphs.Menu, onMenu)
            Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(chat?.title ?: stringResource(R.string.chat_new), transitionSpec = { (fadeIn(tween(200)) + slideInVertically { it / 3 }) togetherWith fadeOut(tween(120)) }, label = "title") { title ->
                    Text(title, style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val subtitle = when {
                    busy -> stringResource(R.string.rs_running)
                    chat?.ultra == true -> "Ultra · " + (chat?.repo ?: stringResource(R.string.ultra_no_repo))
                    orchestra.enabled && orchestra.delegates.isNotEmpty() -> androidx.compose.ui.res.pluralStringResource(R.plurals.orc_on_short, orchestra.delegates.size, orchestra.delegates.size)
                    else -> providers.firstOrNull()?.let { "${it.name} · ${it.model}" } ?: ""
                }
                if (subtitle.isNotBlank()) Text(subtitle, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(Glyphs.Compose) { vm.open(null) }
        }

        Box(Modifier.weight(1f)) {
            val list = messages
            AnimatedContent(
                targetState = list?.isEmpty(),
                transitionSpec = {
                    if (initialState == true && targetState == false) {
                        (fadeIn(tween(320, delayMillis = 160)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 260f)) { it / 6 }) togetherWith
                            (fadeOut(tween(260)) + scaleOut(tween(380), targetScale = 0.72f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.32f)) + slideOutVertically(tween(380)) { -it / 5 })
                    } else {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(140))
                    }
                },
                label = "empty",
            ) { empty ->
            if (empty == true) {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ModeSwitch(pendingMode) { switchMode(it) }
                        AnimatedVisibility(shownUltra, enter = expandVertically(spring(dampingRatio = 0.7f)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            Box(Modifier.padding(top = 10.dp)) { RepoChip(pendingRepo, onPickRepo) }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        EmptyState(horizon, noProvider = providers.isEmpty(), ultra = shownUltra, onSuggestion = { draft = it }, onAccounts = {
                            runCatching { context.startActivity(com.paulneja.quorvi.ui.settings.SettingsActivity.intent(context, com.paulneja.quorvi.ui.settings.SettingsModule.AI)) }
                        })
                    }
                }
            } else if (empty == false) {
                androidx.compose.runtime.CompositionLocalProvider(LocalUltra provides (chat?.ultra == true)) {
                    MessageList(vm, policy, messages.orEmpty(), onImage)
                }
            }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (chat?.ultra == true && chat?.repo == null) RepoChip(null, onPickRepo)
            ReasoningChip(vm, onReasoning)
        }
        val mentionOptions = mentionOptions(chat?.ultra == true || pendingMode == com.paulneja.quorvi.agent.chat.ChatMode.ULTRA, orchestra)
        Composer(
            text = draft,
            onText = { draft = it },
            mentions = mentionOptions,
            commands = if (chat?.ultra == true || pendingMode == com.paulneja.quorvi.agent.chat.ChatMode.ULTRA) slashCommands() else emptyList(),
            files = files,
            busy = busy,
            hint = stringResource(if (waitingQuestion) R.string.chat_hint_answer else R.string.chat_hint),
            onSend = {
                vm.send(draft, files.toList())
                draft = ""
                files.clear()
            },
            onStop = { vm.stop() },
            onVoice = onVoice,
        )
    }
}

@Composable
private fun MessageList(vm: ChatViewModel, policy: PolicyViewModel, messages: List<com.paulneja.quorvi.agent.chat.ChatMessageEntity>, onImage: (ChatAttachment) -> Unit) {
    val context = LocalContext.current
    val state = rememberLazyListState()
    val seen = remember(messages.firstOrNull()?.chatId) { mutableSetOf<String>().apply { messages.forEach { add(it.id) } } }
    val reversed = remember(messages) { messages.asReversed() }
    val lastAssistant = messages.lastOrNull { it.role == ChatRole.ASSISTANT }?.id
    LaunchedEffect(messages.size) { if (state.firstVisibleItemIndex <= 2) state.animateScrollToItem(0) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.Bottom),
        ) {
            items(reversed, key = { it.id }) { m ->
                val animate = seen.add(m.id)
                when (m.role) {
                    ChatRole.USER -> UserBubble(m, animate, onImage)
                    ChatRole.PR -> PrCard(
                        m, animate, onApprove = { vm.approvePr(m.id) }, onReject = { vm.rejectPr(m.id) },
                        onEdit = { t, b, c, excluded, done -> vm.editPr(m.id, t, b, c, excluded, done) },
                        onEditFile = { path, content, done -> vm.editPrFile(m.id, path, content, done) },
                    )
                    ChatRole.PLAN -> PlanCard(m, animate, onApprove = { vm.approvePlan(m.id) }, onChange = { vm.changePlan(m.id, it) })
                    ChatRole.OFFER -> OfferCard(m, animate, onAccept = { vm.acceptUltra(m.id) }, onDismiss = { vm.dismissCard(m.id) })
                    ChatRole.CAPSULE -> {
                        val saved = stringResource(R.string.capsule_draft_saved)
                        CapsuleCard(m, animate, onSave = { vm.saveCapsule(m.id) { id -> if (id != null) android.widget.Toast.makeText(context, saved, android.widget.Toast.LENGTH_SHORT).show() } }, onDismiss = { vm.dismissCard(m.id) })
                    }
                    ChatRole.JOB -> com.paulneja.quorvi.agent.jobs.JobNote.decode(m.text)?.let { note ->
                        JobCard(note, animate, onStop = { vm.stopJob(m.chatId, note.id) })
                    }
                    ChatRole.TASK -> {
                        val v by remember(m.runId) { m.runId?.let { vm.runView(it) } ?: kotlinx.coroutines.flow.flowOf(null) }.collectAsState(null)
                        TaskCard(m, animate, v) {
                            m.runId?.let { id -> runCatching { context.startActivity(Intent(context, OperationsActivity::class.java).putExtra(OperationsActivity.EXTRA_RUN, id)) } }
                        }
                    }
                    else -> {
                        val v by remember(m.runId) { m.runId?.let { vm.runView(it) } ?: kotlinx.coroutines.flow.flowOf(null) }.collectAsState(null)
                        AssistantMessage(m, animate, m.id == lastAssistant, policy, v, onRetry = { vm.retry() }, onImage = onImage)
                    }
                }
            }
        }
        val away by remember { derivedStateOf { state.firstVisibleItemIndex > 1 } }
        val scope = rememberCoroutineScope()
        AnimatedVisibility(away, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), enter = scaleIn(spring(dampingRatio = 0.55f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
            Box(
                Modifier.size(40.dp).liquidGlass(CircleShape, GlassLevel.CHROME).clickable { scope.launch { state.animateScrollToItem(0) } },
                contentAlignment = Alignment.Center,
            ) { Glyph(Glyphs.Down, 18.dp) }
        }
    }
}

@Composable
private fun EmptyState(horizon: com.paulneja.quorvi.ui.chat.horizon.HorizonState, noProvider: Boolean, ultra: Boolean, onSuggestion: (String) -> Unit, onAccounts: () -> Unit) {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = stringResource(
        when (hour) {
            in 5..12 -> R.string.chat_hello_morning
            in 13..19 -> R.string.chat_hello_afternoon
            else -> R.string.chat_hello_night
        },
    )
    val suggestions = if (ultra) listOf(
        Triple(Glyphs.Search, R.string.ultra_sug_explore, R.string.ultra_sug_explore_fill),
        Triple(Glyphs.Bolt, R.string.ultra_sug_fix, R.string.ultra_sug_fix_fill),
        Triple(Glyphs.Plus, R.string.ultra_sug_feature, R.string.ultra_sug_feature_fill),
        Triple(Glyphs.Check, R.string.ultra_sug_tests, R.string.ultra_sug_tests_fill),
    ) else listOf(
        Triple(Glyphs.Brain, R.string.chat_sug_explain, R.string.chat_sug_explain_fill),
        Triple(Glyphs.Pen, R.string.chat_sug_write, R.string.chat_sug_write_fill),
        Triple(Glyphs.Note, R.string.chat_sug_doc, R.string.chat_sug_doc_fill),
        Triple(Glyphs.Terminal, R.string.chat_sug_code, R.string.chat_sug_code_fill),
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        ModeOrb(104.dp, ultra, instant = true, modifier = Modifier.horizonAnchor(horizon).orbTouch())
        Spacer(Modifier.height(22.dp))
        Text(greeting, style = Quorvi.type.headline.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text, textAlign = TextAlign.Center, modifier = Modifier.entrance(true))
        Text(stringResource(if (ultra) R.string.ultra_hello_sub else R.string.chat_hello_sub), style = Quorvi.type.body, color = Quorvi.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp).entrance(true))
        Spacer(Modifier.height(26.dp))
        if (noProvider) {
            Column(
                Modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(22.dp), GlassLevel.PANEL).clickable(onClick = onAccounts).padding(16.dp),
            ) {
                Text(stringResource(R.string.chat_no_model_title), style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text)
                Text(stringResource(R.string.chat_no_model_body), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
            }
        } else {
            val keyboard = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            AnimatedVisibility(!keyboard, enter = fadeIn(tween(180)) + expandVertically(), exit = fadeOut(tween(120)) + shrinkVertically()) {
            Column {
            suggestions.chunked(2).forEachIndexed { row, pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEachIndexed { col, (glyph, label, fill) ->
                        val fillText = stringResource(fill)
                        StaggeredCard(delay = (row * 2 + col) * 70, modifier = Modifier.weight(1f)) {
                            val source = remember { MutableInteractionSource() }
                            Column(
                                Modifier.fillMaxWidth().height(92.dp).liquidPress(source).liquidGlass(RoundedCornerShape(22.dp), GlassLevel.PANEL).orbLit(22.dp)
                                    .clickable(interactionSource = source, indication = null) { onSuggestion(fillText) }.padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Glyph(glyph, 20.dp, Quorvi.colors.accent)
                                Text(stringResource(label), style = Quorvi.type.action, color = Quorvi.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            }
            }
        }
    }
}

@Composable
private fun StaggeredCard(delay: Int, modifier: Modifier, content: @Composable () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay.toLong() + 120)
        p.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 220f))
    }
    Box(modifier.graphicsLayer {
        alpha = p.value.coerceIn(0f, 1f)
        translationY = (1f - p.value) * 60.dp.toPx()
        rotationX = (1f - p.value) * 24f
        cameraDistance = 14f * density
    }) { content() }
}

@Composable
fun Orb(size: Dp) {
    val t = rememberInfiniteTransition(label = "orb")
    val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "a")
    val b by t.animateFloat(360f, 0f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "b")
    val breathe by t.animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "breathe")
    val accent = Quorvi.colors.accent
    val violet = Color(0xFFB98CFF)
    val cyan = Color(0xFF5CE1E6)
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), center, r * 1.4f), r * 1.4f, center)
        rotateLayer(a) { drawCircle(Brush.sweepGradient(listOf(accent, violet, cyan, accent), center), r * 0.98f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.12f)) }
        rotateLayer(b) { drawCircle(Brush.sweepGradient(listOf(Color.Transparent, cyan, Color.Transparent, violet, Color.Transparent), center), r * 0.76f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.07f)) }
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f), accent, accent.copy(alpha = 0.2f)), center, r * 0.5f * breathe), r * 0.48f * breathe, center)
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.rotateLayer(degrees: Float, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    drawContext.transform.rotate(degrees, center)
    block()
    drawContext.transform.rotate(-degrees, center)
}

@Composable
private fun ReasoningChip(vm: ChatViewModel, onClick: () -> Unit) {
    val o by vm.orchestra.collectAsState()
    val effort = o.coordinator?.effort ?: o.effort
    val thinking = o.coordinator?.thinking ?: o.thinking
    val label = buildString {
        append(effort?.let { e -> stringResource(when (e) {
            com.paulneja.quorvi.agent.models.ReasoningEffort.LOW -> R.string.orc_effort_low
            com.paulneja.quorvi.agent.models.ReasoningEffort.MEDIUM -> R.string.orc_effort_medium
            com.paulneja.quorvi.agent.models.ReasoningEffort.HIGH -> R.string.orc_effort_high
            com.paulneja.quorvi.agent.models.ReasoningEffort.MAX -> R.string.orc_effort_max
        }) } ?: stringResource(R.string.orc_effort_auto))
        if (thinking) append(" · ").append(stringResource(R.string.reasoning_thinking_short))
    }
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(Glyphs.Brain, 14.dp, if (thinking || effort != null) Quorvi.colors.accent else Quorvi.colors.textSecondary)
        Spacer(Modifier.width(6.dp))
        Text(label, style = Quorvi.type.caption.copy(fontWeight = FontWeight.Medium), color = Quorvi.colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun IconButton(glyph: ImageVector, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(Modifier.size(44.dp).liquidPress(source).clip(CircleShape).clickable(interactionSource = source, indication = null, onClick = onClick), contentAlignment = Alignment.Center) {
        Glyph(glyph, 22.dp)
    }
}

@Composable
private fun slashCommands(): List<MentionOption> = listOf(
    MentionOption("plan", stringResource(R.string.cmd_plan), stringResource(R.string.cmd_plan_desc), Glyphs.Note),
    MentionOption("pr", stringResource(R.string.cmd_pr), stringResource(R.string.cmd_pr_desc), Glyphs.PullRequest),
    MentionOption("investigar", stringResource(R.string.mention_research), stringResource(R.string.mention_research_desc), Glyphs.Search),
    MentionOption("meta", stringResource(R.string.mention_meta), stringResource(R.string.mention_meta_desc), Glyphs.Bolt),
)

@Composable
private fun mentionOptions(ultra: Boolean, orchestra: com.paulneja.quorvi.agent.orchestra.Orchestra): List<MentionOption> = buildList {
    add(MentionOption("meta", stringResource(R.string.mention_meta), stringResource(R.string.mention_meta_desc), Glyphs.Bolt))
    add(MentionOption("investigar", stringResource(R.string.mention_research), stringResource(R.string.mention_research_desc), Glyphs.Search))
    if (!ultra) add(MentionOption("ultra", stringResource(R.string.mention_ultra), stringResource(R.string.mention_ultra_desc), Glyphs.Git))
    if (orchestra.enabled) orchestra.delegates.forEach { s ->
        val word = com.paulneja.quorvi.agent.chat.Mentions.aliases.entries.firstOrNull { it.value == s }?.key ?: return@forEach
        add(MentionOption(word, s.name, specialistBlurb(s), s.glyph()))
    }
}

@Composable
private fun Composer(
    text: String,
    onText: (String) -> Unit,
    mentions: List<MentionOption>,
    commands: List<MentionOption> = emptyList(),
    files: MutableList<Uri>,
    busy: Boolean,
    hint: String,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onVoice: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { files += it }
    val docs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { files += it }
    val canSend = text.isNotBlank() || files.isNotEmpty()
    val accent = Quorvi.colors.accent
    val mentionLook = remember(accent) { MentionTransformation(accent) }
    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        AnimatedVisibility(menu, enter = expandVertically(spring(dampingRatio = 0.7f, stiffness = 420f)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(bottom = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AttachOption(Glyphs.Image, stringResource(R.string.chat_attach_photos)) { menu = false; photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    AttachOption(Glyphs.File, stringResource(R.string.chat_attach_files)) { menu = false; docs.launch(arrayOf("*/*")) }
                }

                MentionBar(mentions) { o -> menu = false; onText(text.trimEnd().let { if (it.isEmpty()) "@${o.word} " else "$it @${o.word} " }) }
            }
        }
        val partial = activeMention(text)
        val matching = partial?.let { p -> mentions.filter { it.word.startsWith(p, ignoreCase = true) } }.orEmpty()

        val slash = Regex("^/([\\p{L}]*)$").find(text)?.groupValues?.get(1)
        val commandMatches = if (slash != null) commands.filter { it.word.startsWith(slash, ignoreCase = true) } else emptyList()
        AnimatedVisibility(!menu && commandMatches.isNotEmpty(), enter = expandVertically(spring(dampingRatio = 0.75f, stiffness = 520f)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Box(Modifier.padding(bottom = 6.dp)) { MentionBar(commandMatches, prefix = "/") { o -> onText("/${o.word} ") } }
        }
        AnimatedVisibility(!menu && matching.isNotEmpty(), enter = expandVertically(spring(dampingRatio = 0.75f, stiffness = 520f)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Box(Modifier.padding(bottom = 6.dp)) { MentionBar(matching) { o -> onText(completeMention(text, o.word)) } }
        }
        AnimatedVisibility(files.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            LazyRow(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(files.toList(), key = { it.toString() }) { uri -> PendingFile(uri, Modifier.animateItem()) { files.remove(uri) } }
            }
        }
        Row(
            Modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(28.dp), GlassLevel.CHROME).orbLit(28.dp, 0.8f).padding(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            val turn by androidx.compose.animation.core.animateFloatAsState(if (menu) 45f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "turn")
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.07f)).clickable { menu = !menu }, contentAlignment = Alignment.Center) {
                Glyph(Glyphs.Plus, 20.dp, modifier = Modifier.graphicsLayer { rotationZ = turn })
            }

            var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(text, androidx.compose.ui.text.TextRange(text.length))) }
            if (field.text != text) field = androidx.compose.ui.text.input.TextFieldValue(text, androidx.compose.ui.text.TextRange(text.length))
            BasicTextField(
                value = field, onValueChange = { field = it; if (it.text != text) onText(it.text) },
                textStyle = Quorvi.type.body.copy(color = Quorvi.colors.text, fontSize = 16.sp),
                cursorBrush = SolidColor(Quorvi.colors.accent),
                visualTransformation = mentionLook,
                maxLines = 6,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 11.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text(hint, style = Quorvi.type.body.copy(fontSize = 16.sp), color = Quorvi.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    }
                },
            )

            if (onVoice != null && !canSend && !busy) VoiceButton(onVoice)
            else SendButton(busy = busy && !canSend, enabled = canSend || busy, onClick = { if (busy && !canSend) onStop() else if (canSend) onSend() })
        }
    }
}

@Composable
private fun VoiceButton(onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Glyph(Glyphs.Wave, 20.dp, Color(0xFF0B0C10))
    }
}

@Composable
private fun SendButton(busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg by androidx.compose.animation.animateColorAsState(if (enabled) Quorvi.colors.accent else Color.White.copy(alpha = 0.08f), tween(200), label = "bg")
    val scale by androidx.compose.animation.core.animateFloatAsState(if (enabled) 1f else 0.9f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "s")
    Box(
        Modifier.size(42.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(bg).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(busy, transitionSpec = { (scaleIn(spring(dampingRatio = 0.5f)) + fadeIn()) togetherWith (scaleOut() + fadeOut()) }, label = "icon") { b ->
            if (b) Box(Modifier.size(13.dp).clip(RoundedCornerShape(3.dp)).background(Quorvi.colors.onAccent))
            else Glyph(Glyphs.Send, 20.dp, if (enabled) Quorvi.colors.onAccent else Quorvi.colors.textTertiary, Modifier.graphicsLayer { rotationZ = -90f })
        }
    }
}

@Composable
private fun AttachOption(glyph: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(18.dp)).liquidGlass(RoundedCornerShape(18.dp), GlassLevel.CHROME).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(glyph, 18.dp, Quorvi.colors.accent)
        Spacer(Modifier.width(8.dp))
        Text(label, style = Quorvi.type.action, color = Quorvi.colors.text)
    }
}

@Composable
private fun PendingFile(uri: Uri, modifier: Modifier, onRemove: () -> Unit) {
    val context = LocalContext.current
    val mime = remember(uri) { context.contentResolver.getType(uri).orEmpty() }
    Box(modifier.size(64.dp)) {
        if (mime.startsWith("image/")) {
            val bmp = rememberUriBitmap(uri, 256)
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.07f))) {
                bmp?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
            }
        } else {
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.07f)), contentAlignment = Alignment.Center) { Glyph(Glyphs.File, 22.dp, Quorvi.colors.accent) }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(3.dp).size(20.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Glyph(Glyphs.Close, 12.dp) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatDrawer(
    vm: ChatViewModel,
    progress: () -> Float,
    modifier: Modifier,
    onPick: (String) -> Unit,
    onNew: () -> Unit,
    onOrchestra: () -> Unit,
    onMemory: () -> Unit,
    onUltra: () -> Unit,
) {
    val chats by vm.chats.collectAsState()
    val current by vm.chatId.collectAsState()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    val now = remember(chats) { System.currentTimeMillis() }

    var ultraTab by rememberSaveable { mutableStateOf(chats.firstOrNull { it.id == current }?.ultra == true) }
    LaunchedEffect(current) { chats.firstOrNull { it.id == current }?.let { ultraTab = it.ultra } }
    val shownChats = chats.filter { it.ultra == ultraTab }
    var matches by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(query) { kotlinx.coroutines.delay(150); matches = vm.searchChats(query) }
    val groups = remember(chats, query, ultraTab, matches) { ChatLogic.grouped(shownChats.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.id in matches }, now) }
    Column(
        modifier.graphicsLayer {
            val p = progress()
            alpha = p.coerceIn(0f, 1f)
            translationX = -(1f - p) * 60.dp.toPx()
        }.statusBarsPadding().navigationBarsPadding().padding(start = 14.dp, end = 10.dp, top = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            NexoAvatar(working = false, size = 30.dp)
            Spacer(Modifier.width(10.dp))
            Text("Nexo", style = Quorvi.type.title.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text)
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.07f)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(Glyphs.Search, 16.dp, Quorvi.colors.textSecondary)
            Spacer(Modifier.width(8.dp))
            BasicTextField(query, { query = it }, singleLine = true, textStyle = Quorvi.type.body.copy(color = Quorvi.colors.text), cursorBrush = SolidColor(Quorvi.colors.accent), modifier = Modifier.weight(1f),
                decorationBox = { inner -> Box { if (query.isEmpty()) Text(stringResource(R.string.chat_search), style = Quorvi.type.body, color = Quorvi.colors.textTertiary); inner() } })
        }
        DrawerRow(Glyphs.Compose, stringResource(R.string.chat_new), accent = true, onClick = onNew)
        com.paulneja.quorvi.ui.design.QuorviSegmented(
            listOf(false to stringResource(R.string.chat_tab_chats), true to stringResource(R.string.chat_tab_ultra)),
            ultraTab, { ultraTab = it }, Modifier.padding(top = 6.dp),
        )
        LazyColumn(Modifier.weight(1f).padding(top = 6.dp)) {
            groups.forEach { (age, items) ->
                item(key = "h-$age") {
                    Text(stringResource(ageLabel(age)), style = Quorvi.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.textTertiary, modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 4.dp).animateItem())
                }
                items(items, key = { it.id }) { c ->
                    ChatRow(c, selected = c.id == current, editing = editing == c.id, modifier = Modifier.animateItem(),
                        onClick = { onPick(c.id) }, onLongClick = { editing = if (editing == c.id) null else c.id },
                        onRename = { vm.rename(c.id, it); editing = null }, onDelete = { vm.delete(c.id); editing = null })
                }
            }
            if (groups.isEmpty()) item { Text(stringResource(if (query.isNotBlank()) R.string.chat_search_none else if (ultraTab) R.string.chat_none_ultra else R.string.chat_none), style = Quorvi.type.caption, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(12.dp)) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Quorvi.colors.border))
        DrawerRow(Glyphs.Orchestra, stringResource(R.string.orc_title), onClick = onOrchestra)
        DrawerRow(Glyphs.Memory, stringResource(R.string.mem_title), onClick = onMemory)
        DrawerRow(Glyphs.Git, stringResource(R.string.ultra_title), onClick = onUltra)
        DrawerRow(Glyphs.Model, stringResource(R.string.chat_models)) { runCatching { context.startActivity(com.paulneja.quorvi.ui.settings.SettingsActivity.intent(context, com.paulneja.quorvi.ui.settings.SettingsModule.AI)) } }
        if (chats.isNotEmpty()) DrawerRow(Glyphs.Trash, stringResource(if (confirmAll) R.string.chat_delete_all_confirm else R.string.chat_delete_all), danger = true) {
            if (confirmAll) { vm.deleteAll(); confirmAll = false } else confirmAll = true
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DrawerRow(glyph: ImageVector, label: String, accent: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().liquidPress(source).clip(RoundedCornerShape(14.dp)).clickable(interactionSource = source, indication = null, onClick = onClick).padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = when { danger -> Quorvi.colors.danger; accent -> Quorvi.colors.accent; else -> Quorvi.colors.textSecondary }
        Glyph(glyph, 19.dp, tint)
        Spacer(Modifier.width(12.dp))
        Text(label, style = Quorvi.type.action.copy(fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Medium), color = if (danger) Quorvi.colors.danger else Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    c: ChatEntity,
    selected: Boolean,
    editing: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var renaming by remember(editing) { mutableStateOf(false) }
    var confirm by remember(editing) { mutableStateOf(false) }
    var title by remember(c.title, renaming) { mutableStateOf(c.title) }
    val bg by androidx.compose.animation.animateColorAsState(if (selected || editing) Color.White.copy(alpha = 0.09f) else Color.Transparent, tween(180), label = "bg")
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 10.dp, vertical = 11.dp)) {
        if (renaming) {
            BasicTextField(title, { title = it }, singleLine = true, textStyle = Quorvi.type.action.copy(color = Quorvi.colors.text), cursorBrush = SolidColor(Quorvi.colors.accent), modifier = Modifier.fillMaxWidth(),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onRename(title) }),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done))
        } else {
            Text(c.title, style = Quorvi.type.action, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AnimatedVisibility(editing, enter = expandVertically(spring(dampingRatio = 0.75f, stiffness = 420f)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (renaming) Pill(stringResource(R.string.chat_save), accent = true) { onRename(title) }
                else Pill(stringResource(R.string.chat_rename)) { renaming = true }
                Pill(stringResource(if (confirm) R.string.chat_delete_confirm else R.string.chat_delete), danger = true) { if (confirm) onDelete() else confirm = true }
            }
        }
    }
}

@Composable
fun Pill(label: String, accent: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val bg = when { danger -> Quorvi.colors.danger.copy(alpha = 0.16f); accent -> Quorvi.colors.accent; else -> Color.White.copy(alpha = 0.09f) }
    val fg = when { danger -> Quorvi.colors.danger; accent -> Quorvi.colors.onAccent; else -> Quorvi.colors.text }
    Text(label, style = Quorvi.type.caption.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp))
}

private fun ageLabel(age: ChatAge) = when (age) {
    ChatAge.TODAY -> R.string.chat_today
    ChatAge.YESTERDAY -> R.string.chat_yesterday
    ChatAge.WEEK -> R.string.chat_week
    ChatAge.OLDER -> R.string.chat_older
}

@Composable
private fun ImageViewer(f: ChatAttachment, onClose: () -> Unit) {
    val context = LocalContext.current
    val bmp = rememberBitmap(f.path, 2048)
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + pan else Offset.Zero
    }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)) {
        bmp?.let {
            Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().transformable(transform).graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
            })
        }
        Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            f.uri?.let { uri ->
                Pill(stringResource(R.string.chat_open_gallery)) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), f.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) } }
                Pill(stringResource(R.string.chat_share)) {
                    runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(f.mime).putExtra(Intent.EXTRA_STREAM, Uri.parse(uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null)) }
                }
            }
            Pill(stringResource(R.string.chat_close), accent = true, onClick = onClose)
        }
    }
}
