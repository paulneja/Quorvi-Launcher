package com.paulneja.quorvi.labs

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow

@SuppressLint("SdCardPath")
object TermuxBridge {
    const val PACKAGE = "com.termux"
    const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    private const val SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION = "com.termux.RUN_COMMAND"
    private const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_PENDING = "com.termux.RUN_COMMAND_PENDING_INTENT"
    private const val BASH = "/data/data/com.termux/files/usr/bin/bash"
    private const val HOME = "/data/data/com.termux/files/home"

    val last = MutableStateFlow("")

    fun installed(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun run(context: Context, script: String): Result<Unit> = runCatching {
        val callback = PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, TermuxResultReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val intent = Intent(ACTION)
            .setClassName(PACKAGE, SERVICE)
            .putExtra(EXTRA_PATH, BASH)
            .putExtra(EXTRA_ARGS, arrayOf("-c", script))
            .putExtra(EXTRA_WORKDIR, HOME)
            .putExtra(EXTRA_BACKGROUND, true)
            .putExtra(EXTRA_PENDING, callback)
        last.value = "sent at ${System.currentTimeMillis()}"
        context.startService(intent) ?: error("RunCommandService not found")
    }

    private const val REQUEST = 7
}

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = intent.getBundleExtra("result")
        TermuxBridge.last.value = if (result == null) "callback without result bundle" else buildString {
            appendLine("exitCode=${result.getInt("exitCode", -1)} err=${result.getInt("err", 0)} ${result.getString("errmsg").orEmpty()}")
            appendLine("stdout:")
            appendLine(result.getString("stdout").orEmpty())
            appendLine("stderr:")
            append(result.getString("stderr").orEmpty())
        }
    }
}

@Composable
fun TermuxLab() {
    val context = LocalContext.current
    var installed by remember { mutableStateOf(TermuxBridge.installed(context)) }
    var granted by remember { mutableStateOf(TermuxBridge.granted(context)) }
    var script by remember { mutableStateOf("uname -a; echo \$PREFIX; id") }
    val last by TermuxBridge.last.collectAsStateWithLifecycle()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Termux installed: $installed · RUN_COMMAND granted: $granted", style = MaterialTheme.typography.bodyMedium)
        Text("Needs allow-external-apps=true in ~/.termux/termux.properties.", style = MaterialTheme.typography.bodySmall)
        if (installed && !granted) Button(onClick = { ask.launch(TermuxBridge.PERMISSION) }) { Text("Request permission") }
        OutlinedTextField(value = script, onValueChange = { script = it }, label = { Text("bash -c") }, modifier = Modifier.fillMaxWidth())
        Button(enabled = installed && granted, onClick = {
            installed = TermuxBridge.installed(context)
            TermuxBridge.run(context, script).onFailure { TermuxBridge.last.value = "failed: ${it.message}" }
        }) { Text("Run") }
        Text(last, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
}
