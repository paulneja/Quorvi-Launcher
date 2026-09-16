package com.paulneja.quorvi.launcher

import com.paulneja.quorvi.launcher.home.HomeBackup
import com.paulneja.quorvi.launcher.home.HomeItemEntity
import com.paulneja.quorvi.launcher.home.HomePageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeBackupTest {
    @Test
    fun pagesAndBlockConfigSurviveARoundTrip() {
        val items = listOf(HomeItemEntity(id = 7, type = 3, container = 0, page = 1, cellX = 2, spanX = 2, appKey = "a/b", config = "{\"k\":1}"))
        val pages = listOf(HomePageEntity(0, "HOME", null), HomePageEntity(1, "APPS", "Mis apps"))
        val back = HomeBackup.decode(HomeBackup.encode(items, pages))!!
        assertEquals(items, back.items)
        assertEquals(pages, back.pages)
    }

    @Test
    fun theFirstFormatStillRestoresItsItems() {
        val legacy = """[{"id":3,"type":0,"container":0,"page":0,"cellX":1,"cellY":2,"spanX":1,"spanY":1,"appKey":"x/y","widgetId":null,"shortcutId":null,"parentId":null,"title":null,"locked":false,"rank":0}]"""
        val back = HomeBackup.decode(legacy)!!
        assertEquals("x/y", back.items.single().appKey)
        assertNull(back.pages)
        assertNull(HomeBackup.decode("not json"))
    }
}
