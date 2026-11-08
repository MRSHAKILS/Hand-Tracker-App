package com.handtracker.fingerspark

import org.junit.Assert.*
import org.junit.Test

class HomeLayoutPolicyTest {
    @Test fun standardPhoneShowsTwoChoicesSideBySide() {
        assertFalse(HomeLayoutPolicy.forWindow(360, 780, 1f).stackModes)
    }

    @Test fun smallPhoneStacksChoices() {
        assertTrue(HomeLayoutPolicy.forWindow(320, 568, 1f).stackModes)
    }

    @Test fun enlargedTextGetsFullWidthChoices() {
        assertTrue(HomeLayoutPolicy.forWindow(412, 840, 1.5f).stackModes)
    }

    @Test fun tabletContentAndToolbarDoNotStretchAcrossTheScreen() {
        val layout = HomeLayoutPolicy.forWindow(1000, 1200, 1f)
        assertEquals(560, 1000 - 2 * layout.sidePadding)
        assertEquals(352, layout.toolbarWidth)
    }

    @Test fun shortWindowsReserveLessArtworkSpace() {
        assertEquals(140, HomeLayoutPolicy.forWindow(360, 560, 1f).artworkHeight)
        assertEquals(176, HomeLayoutPolicy.forWindow(360, 780, 1f).artworkHeight)
    }

    @Test fun toolbarFitsNarrowWindows() {
        for (width in listOf(280, 320, 360, 412)) {
            assertTrue(HomeLayoutPolicy.forWindow(width, 640, 1f).toolbarWidth <= width - 32)
        }
    }
}
