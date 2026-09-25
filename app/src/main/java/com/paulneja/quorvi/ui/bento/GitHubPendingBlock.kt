package com.paulneja.quorvi.ui.bento

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulneja.quorvi.R
import com.paulneja.quorvi.connections.github.PendingItem
import com.paulneja.quorvi.ui.design.BentoBlock
import com.paulneja.quorvi.ui.design.BlockHeader
import com.paulneja.quorvi.ui.design.BlockState
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.panels.PanelsViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GitHubPendingBlock(panels: PanelsViewModel, onSetup: () -> Unit) {
    val signedIn by panels.signedIn.collectAsStateWithLifecycle()
    val pending by panels.pending.collectAsStateWithLifecycle()
    val ignored by panels.ignoredGitHub.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(signedIn) { if (signedIn) panels.refreshPending() }
    var showIgnored by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf<String?>(null) }
    val all = pending
    val state = when {
        !signedIn -> BlockState.NeedsPermission(stringResource(R.string.gh_pending_signin), stringResource(R.string.gh_pending_connect), onSetup)
        all == null -> BlockState.Loading
        else -> BlockState.Ready
    }
    BentoBlock(state = state, title = stringResource(R.string.blk_gh_pending), glyph = Glyphs.Git, onClick = null) {
        val list = all.orEmpty()
        val visible = list.filter { (it.key in ignored) == showIgnored }
        val prs = list.count { it.pr && it.key !in ignored }
        val issues = list.count { !it.pr && it.key !in ignored }
        BlockHeader(Glyphs.Git, stringResource(R.string.blk_gh_pending)) {
            Text(stringResource(R.string.gh_pending_counts, prs, issues), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).clickable { panels.refreshPending(force = true) }, contentAlignment = Alignment.Center) { Glyph(Glyphs.Retry, 14.dp, Quorvi.colors.textSecondary) }
        }
        Spacer(Modifier.padding(top = 6.dp))
        if (visible.isEmpty()) {
            Text(stringResource(if (showIgnored) R.string.gh_pending_no_ignored else R.string.gh_pending_none), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(vertical = 6.dp))
        }
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(visible, key = { it.key }) { item ->
                PendingRow(
                    item, armed = armed == item.key, ignored = showIgnored,
                    modifier = Modifier.animateItem(),
                    onOpen = { if (armed == item.key) armed = null else runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                    onHold = { armed = if (armed == item.key) null else item.key },
                    onToggle = { panels.ignoreGitHub(item.key, !showIgnored); armed = null },
                )
            }
        }
        val hidden = list.count { it.key in ignored }
        if (hidden > 0 || showIgnored) {
            Text(
                if (showIgnored) stringResource(R.string.gh_pending_back) else androidx.compose.ui.res.pluralStringResource(R.plurals.gh_pending_ignored, hidden, hidden),
                style = Quorvi.type.caption.copy(fontWeight = FontWeight.Medium), color = Quorvi.colors.accent,
                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { showIgnored = !showIgnored; armed = null }.padding(vertical = 4.dp, horizontal = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PendingRow(item: PendingItem, armed: Boolean, ignored: Boolean, modifier: Modifier, onOpen: () -> Unit, onHold: () -> Unit, onToggle: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).combinedClickable(onClick = onOpen, onLongClick = onHold).padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = when { item.review -> Quorvi.colors.warning; item.pr -> Quorvi.colors.accent; else -> Quorvi.colors.success }
        Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Glyph(if (item.pr) Glyphs.PullRequest else Glyphs.Info, 14.dp, tint)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = Quorvi.type.caption.copy(fontWeight = FontWeight.Medium), color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${item.repo.substringAfter('/')}#${item.number}" + if (item.review) " · " + stringResource(R.string.gh_pending_review) else "",
                style = Quorvi.type.label, color = Quorvi.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        AnimatedVisibility(armed, enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End), exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)) {
            Text(
                stringResource(if (ignored) R.string.gh_pending_unignore else R.string.gh_pending_ignore),
                style = Quorvi.type.label.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text,
                modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(10.dp)).background(Quorvi.colors.overlayActive).clickable(onClick = onToggle).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
