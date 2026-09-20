package com.paulneja.quorvi.runtime

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast

class SaveAsActivity : Activity() {
    private var source: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = savedInstanceState?.getString(KEY)?.let(Uri::parse) ?: intent.data
        val uri = source
        if (uri == null || uri.authority != MediaStore.AUTHORITY) {
            finish()
            return
        }
        if (savedInstanceState == null) {
            val pick = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(intent.type ?: contentResolver.getType(uri) ?: "application/octet-stream")
                .putExtra(Intent.EXTRA_TITLE, intent.getStringExtra(Intent.EXTRA_TITLE) ?: uri.lastPathSegment)
            startActivityForResult(pick, REQUEST)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY, source?.toString())
    }

    @Deprecated("Activity result API is not used in this tiny companion")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val target = data?.data
        val from = source
        if (requestCode == REQUEST && resultCode == RESULT_OK && target != null && from != null) {
            val copied = runCatching {
                contentResolver.openInputStream(from)!!.use { input -> contentResolver.openOutputStream(target, "wt")!!.use { input.copyTo(it) } }
            }
            Toast.makeText(this, if (copied.isSuccess) getString(R.string.saved) else copied.exceptionOrNull()?.message, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private companion object {
        const val REQUEST = 7
        const val KEY = "source"
    }
}
