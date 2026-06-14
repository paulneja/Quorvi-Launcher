package com.paulneja.quorvi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.paulneja.quorvi.ui.design.Geist

private val scheme = darkColorScheme(
    primary = Color(0xFF5EA7FF),
    onPrimary = Color(0xFF08121F),
    surface = Color(0xFF0C0F14),
    onSurface = Color(0xFFF4F5F7),
    background = Color(0xFF0C0F14),
    onBackground = Color(0xFFF4F5F7),
    error = Color(0xFFFF6B6B),
)

private val typography = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontFamily = Geist),
        bodyMedium = bodyMedium.copy(fontFamily = Geist),
        bodySmall = bodySmall.copy(fontFamily = Geist),
        labelLarge = labelLarge.copy(fontFamily = Geist),
        labelMedium = labelMedium.copy(fontFamily = Geist),
        titleMedium = titleMedium.copy(fontFamily = Geist),
        titleLarge = titleLarge.copy(fontFamily = Geist),
    )
}

@Composable
fun QuorviTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
