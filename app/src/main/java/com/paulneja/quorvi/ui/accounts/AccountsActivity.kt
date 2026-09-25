package com.paulneja.quorvi.ui.accounts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulneja.quorvi.R
import com.paulneja.quorvi.agent.models.CopilotParsing
import com.paulneja.quorvi.agent.models.ModelRole
import com.paulneja.quorvi.agent.models.ProviderEntity
import com.paulneja.quorvi.agent.models.ProviderKind
import com.paulneja.quorvi.connections.github.SignInState
import com.paulneja.quorvi.ui.design.ActionRow
import com.paulneja.quorvi.ui.design.ChoiceRow
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Hint
import com.paulneja.quorvi.ui.design.InputField
import com.paulneja.quorvi.ui.design.Label
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.Panel
import com.paulneja.quorvi.ui.settings.SettingsActivity
import com.paulneja.quorvi.ui.settings.SettingsModule

class AccountsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(SettingsActivity.intent(this, SettingsModule.AI))
        finish()
    }
}

@Composable
fun GitHubAccountPanel(viewModel: AccountsViewModel) {
    val signIn by viewModel.signIn.collectAsStateWithLifecycle()
    val login by viewModel.login.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = Quorvi.colors
    Panel {
        when {
            !viewModel.available -> Hint(stringResource(R.string.accounts_github_unavailable))
            login != null -> {
                IdentityRow(Glyphs.Git, "@" + login!!, stringResource(R.string.accounts_github_connected))
                ActionRow(stringResource(R.string.accounts_sign_out), null, danger = true) { viewModel.signOut() }
            }
            else -> when (val s = signIn) {
                is SignInState.Waiting -> {
                    Hint(stringResource(R.string.accounts_enter_code))
                    Text(
                        s.userCode,
                        style = Quorvi.type.title.copy(fontFamily = FontFamily.Monospace, fontSize = 30.sp, letterSpacing = 4.sp),
                        color = colors.text,
                        modifier = Modifier.padding(vertical = 8.dp).clickable {
                            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("code", s.userCode))
                        },
                    )
                    ActionRow(stringResource(R.string.accounts_open_github), s.uri) {
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("code", s.userCode))
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.uri))) }
                    }
                    ActionRow(stringResource(R.string.pin_cancel), null) { viewModel.signInCancel() }
                }
                SignInState.Requesting -> Hint(stringResource(R.string.accounts_requesting))
                is SignInState.Failed -> {
                    Hint(stringResource(R.string.accounts_failed, s.reason))
                    ActionRow(stringResource(R.string.accounts_sign_in), null) { viewModel.signInStart() }
                }
                else -> ActionRow(stringResource(R.string.accounts_sign_in), stringResource(R.string.accounts_sign_in_hint)) { viewModel.signInStart() }
            }
        }
    }
}

@Composable
fun WatchedReposPanel(viewModel: AccountsViewModel) {
    val watched by viewModel.watched.collectAsStateWithLifecycle()
    val repos by viewModel.repos.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    val colors = Quorvi.colors
    Panel {
        if (watched.isEmpty()) Hint(stringResource(R.string.accounts_watched_none))
        watched.forEach { repo ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(repo.fullName, style = Quorvi.type.body, color = colors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.accounts_unwatch), style = Quorvi.type.action, color = colors.textSecondary, modifier = Modifier.clip(Quorvi.shapes.control).clickable { viewModel.toggleWatch(repo.fullName, false) }.padding(8.dp))
            }
        }
        ActionRow(stringResource(if (picking) R.string.accounts_add_repos_done else R.string.accounts_add_repos), null) { picking = !picking; if (picking && repos == null) viewModel.loadRepos() }
        AnimatedVisibility(picking, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                InputField(filter, { filter = it }, stringResource(R.string.accounts_filter))
                val list = repos
                if (list == null) Hint(stringResource(R.string.accounts_loading)) else {
                    val names = watched.map { it.fullName }.toSet()
                    list.filter { filter.isBlank() || it.fullName.contains(filter, true) }.take(40).forEach { repo ->
                        ChoiceRow(repo.fullName + if (repo.private) " · " + stringResource(R.string.accounts_private) else "", repo.fullName in names) {
                            viewModel.toggleWatch(repo.fullName, repo.fullName !in names)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CopilotPanel(viewModel: AccountsViewModel) {
    val copilot by viewModel.copilot.collectAsStateWithLifecycle()
    Panel {
        val status = copilot
        when {
            status == null -> Hint(stringResource(R.string.accounts_loading))
            status.isFailure -> Hint(stringResource(R.string.accounts_failed, status.exceptionOrNull()?.message.orEmpty()))
            status.getOrNull() == null -> Hint(stringResource(R.string.accounts_no_copilot))
            else -> {
                val s = status.getOrNull()!!
                IdentityRow(Glyphs.Sparkle, stringResource(R.string.accounts_copilot_plan, s.plan), s.premium?.let { q ->
                    if (q.unlimited) stringResource(R.string.accounts_copilot_unlimited) else stringResource(R.string.accounts_copilot_quota, q.remaining.toInt(), q.entitlement.toInt())
                })
            }
        }
    }
}

@Composable
fun ProvidersPanel(viewModel: AccountsViewModel) {
    val providers by viewModel.modelProviders.collectAsStateWithLifecycle()
    val login by viewModel.login.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf<ProviderKind?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    Panel {
        if (providers.isEmpty()) Hint(stringResource(R.string.accounts_models_none)) else Hint(stringResource(R.string.mod_ai_providers_hint))
        providers.forEach { p ->
            ProviderRow(p, expanded = open == p.id, onToggle = { open = if (open == p.id) null else p.id.also { viewModel.loadModels(it) } }, onRemove = { viewModel.removeProvider(p.id) })
            AnimatedVisibility(open == p.id, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                val r = models[p.id]
                com.paulneja.quorvi.ui.design.ModelPicker(
                    models = r?.getOrNull(),
                    selected = p.model,
                    onSelect = { viewModel.setModel(p.id, it) },
                    error = r?.exceptionOrNull()?.let { it.message ?: it::class.simpleName.orEmpty() },
                    onRetry = { viewModel.loadModels(p.id) },
                )
            }
        }
    }
    if (adding == null) {
        com.paulneja.quorvi.ui.design.Section(stringResource(R.string.accounts_add_provider))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (login != null) ProviderOption(Glyphs.Git, stringResource(R.string.accounts_add_copilot), stringResource(R.string.accounts_add_copilot_hint)) { adding = ProviderKind.COPILOT }
            ProviderOption(Glyphs.Brain, stringResource(R.string.accounts_add_anthropic), stringResource(R.string.accounts_add_anthropic_hint)) { adding = ProviderKind.ANTHROPIC }
            ProviderOption(Glyphs.Globe, stringResource(R.string.accounts_add_openai), stringResource(R.string.accounts_add_openai_hint)) { adding = ProviderKind.OPENAI_COMPATIBLE }
            if (login != null) ProviderOption(Glyphs.Lan, stringResource(R.string.accounts_add_bridge), stringResource(R.string.accounts_add_bridge_hint)) { adding = ProviderKind.BRIDGE }
            if (login == null) Text(stringResource(R.string.accounts_copilot_needs_github), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        }
    } else {
        Panel { AddProvider(adding!!, viewModel, onDone = { adding = null }) }
    }
}

@Composable
private fun IdentityRow(glyph: ImageVector, title: String, subtitle: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphTile(glyph)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Quorvi.type.action, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary) }
        }
    }
}

@Composable
private fun GlyphTile(glyph: ImageVector) {
    Box(Modifier.size(38.dp).clip(Quorvi.shapes.control).background(Quorvi.colors.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Image(rememberVectorPainter(glyph), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(Quorvi.colors.accent))
    }
}

private fun kindGlyph(kind: String): ImageVector = when (kind) {
    ProviderKind.COPILOT.name -> Glyphs.Git
    ProviderKind.BRIDGE.name -> Glyphs.Lan
    ProviderKind.ANTHROPIC.name -> Glyphs.Brain
    else -> Glyphs.Globe
}

@Composable
private fun roleLabel(role: ModelRole) = stringResource(when (role) {
    ModelRole.QUICK -> R.string.role_quick
    ModelRole.PLAN -> R.string.role_plan
    ModelRole.VISION -> R.string.role_vision
})

@Composable
private fun ProviderRow(p: ProviderEntity, expanded: Boolean, onToggle: () -> Unit, onRemove: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(Quorvi.shapes.control).clickable(onClick = onToggle).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphTile(kindGlyph(p.kind))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = Quorvi.type.action, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(p.model, style = Quorvi.type.mono, color = Quorvi.colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val roles = p.roles.split(',').mapNotNull { r -> ModelRole.entries.firstOrNull { it.name == r.trim() } }
            if (roles.isNotEmpty()) Text(roles.map { roleLabel(it) }.joinToString(" · "), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
        }
        Text(
            stringResource(if (confirm) R.string.accounts_remove_confirm else R.string.menu_remove),
            style = Quorvi.type.action, color = Quorvi.colors.danger,
            modifier = Modifier.clip(Quorvi.shapes.control).clickable { if (confirm) onRemove() else confirm = true }.padding(8.dp),
        )
    }
    if (expanded) Hint(stringResource(R.string.accounts_pick_model))
}

@Composable
private fun ProviderOption(glyph: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Quorvi.shapes.panel).background(Quorvi.colors.field).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphTile(glyph)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Quorvi.type.action, color = Quorvi.colors.text)
            Text(subtitle, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
        }
        Image(rememberVectorPainter(Glyphs.Plus), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(Quorvi.colors.textSecondary))
    }
}

object ProviderPresets {
    data class Preset(val name: String, val url: String, val note: Int? = null)
    val all = listOf(
        Preset("OpenAI", "https://api.openai.com/v1"),
        Preset("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", R.string.preset_gemini_note),
        Preset("OpenRouter", "https://openrouter.ai/api/v1", R.string.preset_openrouter_note),
        Preset("Groq", "https://api.groq.com/openai/v1"),
        Preset("DeepSeek", "https://api.deepseek.com/v1"),
        Preset("Mistral", "https://api.mistral.ai/v1"),
        Preset("xAI", "https://api.x.ai/v1"),
        Preset("Ollama", "http://127.0.0.1:11434/v1", R.string.preset_ollama_note),
    )
}

@Composable
private fun AddProvider(kind: ProviderKind, viewModel: AccountsViewModel, onDone: () -> Unit) {
    val probe by viewModel.probe.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf(when (kind) { ProviderKind.COPILOT -> CopilotParsing.DEFAULT_API; ProviderKind.ANTHROPIC -> com.paulneja.quorvi.agent.models.AnthropicClient.DEFAULT_URL; else -> "" }) }
    var secret by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(when (kind) { ProviderKind.COPILOT -> "Copilot"; ProviderKind.BRIDGE -> "Bridge"; ProviderKind.ANTHROPIC -> "Claude"; else -> "" }) }
    var model by remember { mutableStateOf("") }
    var roles by remember { mutableStateOf(setOf(ModelRole.QUICK, ModelRole.PLAN)) }
    Label(stringResource(R.string.accounts_new_provider))
    if (kind == ProviderKind.OPENAI_COMPATIBLE) {
        Text(stringResource(R.string.accounts_preset), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
            items(ProviderPresets.all.size) { i ->
                val preset = ProviderPresets.all[i]
                val on = url == preset.url && name == preset.name
                Text(
                    preset.name, style = Quorvi.type.action,
                    color = if (on) Quorvi.colors.text else Quorvi.colors.textSecondary,
                    modifier = Modifier.clip(Quorvi.shapes.pill).background(if (on) Quorvi.colors.overlayActive else Quorvi.colors.field).clickable { name = preset.name; url = preset.url }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        ProviderPresets.all.firstOrNull { it.url == url }?.note?.let { Hint(stringResource(it)) }
    }
    InputField(name, { name = it }, stringResource(R.string.accounts_name))
    if (kind == ProviderKind.OPENAI_COMPATIBLE || kind == ProviderKind.BRIDGE) InputField(url, { url = it }, stringResource(if (kind == ProviderKind.BRIDGE) R.string.accounts_bridge_url else R.string.accounts_base_url))
    if (kind != ProviderKind.COPILOT) InputField(secret, { secret = it }, stringResource(if (kind == ProviderKind.BRIDGE) R.string.accounts_pairing_code else R.string.accounts_api_key), secret = true)
    androidx.compose.runtime.LaunchedEffect(kind, url, secret) {
        if (kind == ProviderKind.COPILOT || (url.startsWith("http") && (kind == ProviderKind.BRIDGE || secret.isNotBlank()))) {
            kotlinx.coroutines.delay(600)
            viewModel.probe(kind, url, secret)
        }
    }
    when (val p = probe) {
        Probe.Working -> Hint(stringResource(R.string.accounts_testing))
        is Probe.Failed -> com.paulneja.quorvi.ui.design.ModelPicker(null, model, { model = it }, error = p.reason, onRetry = { viewModel.probe(kind, url, secret) })
        is Probe.Ok -> {
            Hint(stringResource(R.string.accounts_test_ok, p.detail, p.models.size))
            com.paulneja.quorvi.ui.design.ModelPicker(p.models.distinctBy { it.id }.sortedBy { it.displayName.lowercase() }, model, { model = it })
        }
        Probe.Idle -> InputField(model, { model = it }, stringResource(R.string.accounts_model))
    }
    Label(stringResource(R.string.accounts_roles))
    ModelRole.entries.forEach { role ->
        val on = role in roles
        Row(Modifier.fillMaxWidth().clip(Quorvi.shapes.control).clickable { roles = if (on) roles - role else roles + role }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(roleLabel(role), style = Quorvi.type.body, color = Quorvi.colors.text)
                Text(stringResource(when (role) { ModelRole.QUICK -> R.string.role_quick_desc; ModelRole.PLAN -> R.string.role_plan_desc; ModelRole.VISION -> R.string.role_vision_desc }), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
            }
            androidx.compose.material3.Checkbox(on, { roles = if (on) roles - role else roles + role })
        }
    }
    ActionRow(stringResource(R.string.pin_cancel), null) { viewModel.resetProbe(); onDone() }
    if (model.isNotBlank() && (kind == ProviderKind.COPILOT || url.isNotBlank())) {
        ActionRow(stringResource(R.string.accounts_save), null) {
            viewModel.addProvider(kind, name, url, model, secret, roles)
            onDone()
        }
    }
}
