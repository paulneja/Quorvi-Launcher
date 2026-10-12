package com.paulneja.quorvi.ops

import android.app.Activity
import android.os.Bundle

class CoreWakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
        overridePendingTransition(0, 0)
    }
}
