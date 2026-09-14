package com.paulneja.quorvi.launcher

import com.paulneja.quorvi.launcher.apps.DrawerIndex
import com.paulneja.quorvi.launcher.apps.LetterSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrawerIndexTest {
    @Test
    fun accentsFoldAndDigitsGoToHash() {
        assertEquals('A', DrawerIndex.letterOf("Ábaco"))
        assertEquals('N', DrawerIndex.letterOf("Ñandú"))
        assertEquals('E', DrawerIndex.letterOf("éxito"))
        assertEquals('#', DrawerIndex.letterOf("3D Viewer"))
        assertEquals('#', DrawerIndex.letterOf(""))
    }

    @Test
    fun sectionsGroupConsecutiveLetters() {
        val s = DrawerIndex.sections(listOf("1Password", "Ajustes", "Amazon", "Brave", "Cámara", "Chrome"))
        assertEquals(listOf(LetterSection('#', 0, 1), LetterSection('A', 1, 2), LetterSection('B', 3, 1), LetterSection('C', 4, 2)), s)
    }

    @Test
    fun headerPositionsAccountForHeadersAndLeadingRows() {
        val s = DrawerIndex.sections(listOf("Ajustes", "Amazon", "Brave", "Chrome"))
        assertEquals(mapOf('A' to 1, 'B' to 4, 'C' to 6), DrawerIndex.headerPositions(s, leading = 1))
    }

    @Test
    fun railFractionPicksExistingLetter() {
        val letters = listOf('A', 'B', 'C', 'Z')
        assertEquals('A', DrawerIndex.letterAt(0f, letters))
        assertEquals('C', DrawerIndex.letterAt(0.6f, letters))
        assertEquals('Z', DrawerIndex.letterAt(1f, letters))
        assertNull(DrawerIndex.letterAt(0.5f, emptyList()))
    }

    @Test
    fun orderKeepsEachLetterInOneRunSoKeysAreUnique() {
        val labels = listOf("Cámara", "calculadora", "Brave", "Chrome", "9gag", "ábaco", "Zoom", "camera")
        val ordered = DrawerIndex.order(labels) { it }
        val letters = DrawerIndex.sections(ordered).map { it.letter }
        assertEquals(letters.distinct(), letters)
        assertEquals(listOf('#', 'A', 'B', 'C', 'Z'), letters)
    }
}
