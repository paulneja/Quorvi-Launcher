package com.paulneja.quorvi.ui.canvas

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paulneja.quorvi.R
import com.paulneja.quorvi.runtime.RuntimeAvailability
import com.paulneja.quorvi.ui.design.BentoBlock
import com.paulneja.quorvi.ui.design.BlockHeader
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Quorvi
import kotlin.math.roundToInt

object QuorviShell {
    fun open(context: Context, view: View?, from: Rect?, mountStorage: Boolean = false) {
        val intent = Intent(context, com.paulneja.quorvi.runtime.TerminalActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("mount_storage", mountStorage)
        val options = if (view != null && from != null && !from.isEmpty) {
            ActivityOptions.makeScaleUpAnimation(view, from.left.roundToInt(), from.top.roundToInt(), from.width.roundToInt().coerceAtLeast(1), from.height.roundToInt().coerceAtLeast(1)).toBundle()
        } else {
            null
        }
        runCatching { context.startActivity(intent, options) }
            .onFailure { Toast.makeText(context, context.getString(R.string.shell_missing), Toast.LENGTH_LONG).show() }
    }
}
