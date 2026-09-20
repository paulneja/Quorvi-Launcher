package com.paulneja.quorvi.benchmark

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

const val TARGET = "quorvi.app"

fun MacrobenchmarkScope.startHome() {
    startActivityAndWait(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(TARGET).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    device.wait(Until.hasObject(By.pkg(TARGET).depth(0)), 5_000)
}

fun MacrobenchmarkScope.openDrawer() {
    val w = device.displayWidth
    val h = device.displayHeight
    device.swipe(w / 2, (h * 0.72).toInt(), w / 2, (h * 0.25).toInt(), 12)
    device.waitForIdle()
}

fun MacrobenchmarkScope.scrollDrawer() {
    val w = device.displayWidth
    val h = device.displayHeight
    repeat(2) {
        device.swipe(w / 2, (h * 0.8).toInt(), w / 2, (h * 0.3).toInt(), 8)
        device.waitForIdle()
        device.swipe(w / 2, (h * 0.3).toInt(), w / 2, (h * 0.8).toInt(), 8)
        device.waitForIdle()
    }
}

fun MacrobenchmarkScope.closeDrawer() {
    device.pressBack()
    device.waitForIdle()
}

fun MacrobenchmarkScope.swipePages() {
    val w = device.displayWidth
    val h = device.displayHeight
    device.swipe((w * 0.85).toInt(), h / 2, (w * 0.15).toInt(), h / 2, 10)
    device.waitForIdle()
    device.swipe((w * 0.15).toInt(), h / 2, (w * 0.85).toInt(), h / 2, 10)
    device.waitForIdle()
}
