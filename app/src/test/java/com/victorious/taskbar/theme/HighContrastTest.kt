package com.victorious.taskbar.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class HighContrastTest {
    private val dark = FleetPalette(0xFF0B0C12.toInt(), 0xFF161821.toInt(), 0xFF00E5FF.toInt(), 0xFFE840FF.toInt(),
        0xFFF5F7FF.toInt(), 0xFF9AA3B5.toInt(), 0xFFFF6B8A.toInt(), 0xFFFFC14D.toInt(), 0xCC1A1D2B.toInt())

    @Test fun darkBecomesPureBlackWithWhiteText() {
        val hc = dark.highContrast()
        assertEquals(0xFF000000.toInt(), hc.background)
        assertEquals(0xFF000000.toInt(), hc.tile) // opaque, no see-through
        assertEquals(0xFFFFFFFF.toInt(), hc.onBackground)
        assertEquals(dark.primary, hc.primary) // accents kept
    }

    @Test fun lightBecomesPureWhiteWithBlackText() {
        val hc = dark.copy(background = 0xFFFAFAFA.toInt()).highContrast()
        assertEquals(0xFFFFFFFF.toInt(), hc.background)
        assertEquals(0xFF000000.toInt(), hc.onBackground)
    }
}
