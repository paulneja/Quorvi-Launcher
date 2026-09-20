package com.paulneja.quorvi.runtime

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast

class OpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        if (uri != null && uri.authority == MediaStore.AUTHORITY) {
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, intent.type ?: contentResolver.getType(uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { startActivity(Intent.createChooser(view, null)) }
                .onFailure { Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show() }
        }
        finish()
    }
}
