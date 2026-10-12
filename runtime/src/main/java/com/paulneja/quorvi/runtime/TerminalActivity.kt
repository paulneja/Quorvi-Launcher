package com.paulneja.quorvi.runtime

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast

class TerminalActivity : Activity() {
    private lateinit var pane: TerminalPane

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = TerminalPane.BG
        window.navigationBarColor = TerminalPane.BG
        pane = TerminalPane(this, onStorage = ::mountStorage, onExit = ::finish)
        setContentView(pane)
        if (intent.getBooleanExtra(EXTRA_MOUNT, false)) mountStorage()
        pane.start()
    }

    private fun mountStorage() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_REQUEST)
            return
        }
        pane.linkStorage()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_REQUEST && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) pane.linkStorage()
        else if (requestCode == STORAGE_REQUEST) Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        if (isFinishing) pane.release()
        super.onDestroy()
    }

    private companion object {
        const val EXTRA_MOUNT = "mount_storage"
        const val STORAGE_REQUEST = 7
    }
}
