package com.paulneja.quorvi.ui.design

import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun View.hideKeyboard() {
    ViewCompat.getWindowInsetsController(this)?.hide(WindowInsetsCompat.Type.ime())
    context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(windowToken, 0)
}
