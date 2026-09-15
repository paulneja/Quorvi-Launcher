package com.paulneja.quorvi.launcher

import android.content.ComponentName
import android.util.Xml
import com.paulneja.quorvi.launcher.icons.IconPacks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IconPacksTest {
    @Test
    fun parsesComponentsWithRelativeClassNames() {
        assertEquals(ComponentName("com.a", "com.a.Main"), IconPacks.parseComponent("ComponentInfo{com.a/com.a.Main}"))
        assertEquals(ComponentName("com.a", "com.a.ui.Main"), IconPacks.parseComponent("ComponentInfo{com.a/.ui.Main}"))
        assertNull(IconPacks.parseComponent("ComponentInfo{broken}"))
        assertNull(IconPacks.parseComponent(":CALENDAR"))
    }

    @Test
    fun readsAppFilterItems() {
        val xml = """<resources>
            <iconback img1="back"/>
            <item component="ComponentInfo{com.a/com.a.Main}" drawable="a_icon"/>
            <item component=":CALENDAR" drawable="calendar"/>
            <item component="ComponentInfo{com.b/.B}" drawable="b_icon"/>
        </resources>"""
        val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
        val map = IconPacks.parseAppFilter(parser)
        assertEquals(2, map.size)
        assertEquals("b_icon", map[ComponentName("com.b", "com.b.B")])
    }
}
