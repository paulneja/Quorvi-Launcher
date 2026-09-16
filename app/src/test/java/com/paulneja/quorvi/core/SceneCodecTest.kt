package com.paulneja.quorvi.core

import com.paulneja.quorvi.core.scene.Scene
import com.paulneja.quorvi.core.scene.SceneAppearance
import com.paulneja.quorvi.core.scene.SceneCodec
import com.paulneja.quorvi.core.scene.SceneGrid
import com.paulneja.quorvi.core.scene.SceneItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneCodecTest {
    private val scene = Scene(
        schemaVersion = 1,
        exportedAt = 1,
        grid = SceneGrid("AUTO", 4, 5),
        appearance = SceneAppearance(0xFF5EA7FF, 0.7f, "CINEMATIC", 1f, true),
        items = listOf(
            SceneItem("clock", "page", w = 4, h = 2),
            SceneItem("app", "dock", x = 1, app = "com.a/com.a.Main"),
            SceneItem("folder", "page", y = 2, title = "Work", children = listOf("com.b/com.b.B", "com.c/com.c.C")),
        ),
    )

    @Test
    fun roundTrips() {
        assertEquals(scene, SceneCodec.decode(SceneCodec.encode(scene)).getOrThrow())
    }
}
