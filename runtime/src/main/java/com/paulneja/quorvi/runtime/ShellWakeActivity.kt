package com.paulneja.quorvi.runtime

import android.app.Activity
import android.os.Bundle

class ShellWakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
        overridePendingTransition(0, 0)
    }
}
