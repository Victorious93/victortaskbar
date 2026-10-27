package com.victorious.taskbar.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetThemeTest {
    private val specJson = """
        {"schemaVersion":1,"mode":"NEON_FLEET","iconStyle":"SYSTEM","iconPackPackage":"","updatedAtMs":42,
         "futureField":true,
         "palette":{"background":"#FF0B0C12","surface":"#FF161821","primary":"#FF00E5FF","secondary":"#FFE840FF",
         "onBackground":"#FFF5F7FF","muted":"#FF9AA3B5","danger":"#FFFF6B8A","stale":"#FFFFC14D","tile":"#CC1A1D2B"}}
    """.trimIndent()

    private fun row(version: Any? = 1L, json: String? = specJson) = mapOf<String, Any?>(
        "schemaVersion" to version, "mode" to "DARK", "json" to json, "updatedAtMs" to 7L,
        "background" to "#121212", "surface" to "#FF1E1E1E", "primary" to "#FF80DEEA", "secondary" to "#FFCE93D8",
        "onBackground" to "#FFEEEEEE", "muted" to "#FF9E9E9E", "danger" to "#FFEF9A9A", "stale" to "#FFFFCC80",
        "tile" to "#CC2A2A2A", "someNewColumn" to "ignored",
    )

    @Test fun parsesSpecExampleJson() {
        val r = FleetThemeParser.parseJson(specJson) as ParseResult.Ok
        assertEquals(ThemeSource.FLEET, r.state.source)
        assertEquals("NEON_FLEET", r.state.mode)
        assertEquals(42L, r.state.updatedAtMs)
        assertEquals(0xFF00E5FF.toInt(), r.state.palette.primary)
        assertEquals(0xCC1A1D2B.toInt(), r.state.palette.tile)
    }

    @Test fun hexAcceptsSixAndEightDigitsOnly() {
        assertEquals(0xFF121212.toInt(), FleetThemeParser.parseHex("#121212"))
        assertEquals(0x80FFFFFF.toInt(), FleetThemeParser.parseHex("#80ffffff"))
        assertNull(FleetThemeParser.parseHex("#FFF"))
        assertNull(FleetThemeParser.parseHex("#GG0000"))
        assertNull(FleetThemeParser.parseHex(null))
    }

    @Test fun rowPrefersJsonAndIgnoresUnknownColumns() {
        val r = FleetThemeParser.parseRow(row()) as ParseResult.Ok
        assertEquals("NEON_FLEET", r.state.mode)
    }

    @Test fun rowFallsBackToFlatColumnsWhenJsonMissing() {
        val r = FleetThemeParser.parseRow(row(json = null)) as ParseResult.Ok
        assertEquals("DARK", r.state.mode)
        assertEquals(0xFF121212.toInt(), r.state.palette.background)
    }

    @Test fun rejectsSchemaVersionNotOne() {
        assertTrue(FleetThemeParser.parseJson(specJson.replace("\"schemaVersion\":1", "\"schemaVersion\":2")) is ParseResult.Rejected)
        assertTrue(FleetThemeParser.parseJson(specJson.replace("\"schemaVersion\":1,", "")) is ParseResult.Rejected)
        assertTrue(FleetThemeParser.parseRow(row(version = 2L)) is ParseResult.Rejected)
        assertTrue(FleetThemeParser.parseRow(row(version = null)) is ParseResult.Rejected)
        // Row says v1 but the json says v2: json is rejected, flat v1 columns still apply.
        val mixed = FleetThemeParser.parseRow(row(json = specJson.replace("\"schemaVersion\":1", "\"schemaVersion\":2")))
        assertEquals("DARK", (mixed as ParseResult.Ok).state.mode)
    }

    @Test fun rejectsBadPaletteAndGarbage() {
        assertTrue(FleetThemeParser.parseJson(specJson.replace("#FF00E5FF", "cyan")) is ParseResult.Rejected)
        assertTrue(FleetThemeParser.parseJson("not json") is ParseResult.Rejected)
    }

    @Test fun fallbackOnSecurityException() {
        val s = FleetThemeResolver.resolve({ true }) { throw SecurityException("untrusted") }
        assertEquals(ThemeSource.BUILT_IN, s.source)
        assertEquals(BuiltInTheme.NEON, s.palette)
    }

    @Test fun fallbackWhenPinFailsAndNeverQueries() {
        var queried = false
        val s = FleetThemeResolver.resolve({ false }) { queried = true; row() }
        assertEquals(BuiltInTheme.NEON, s.palette)
        assertFalse(queried)
    }

    @Test fun fallbackOnRejectedSchemaNullRowAndOtherErrors() {
        assertEquals(BuiltInTheme.NEON, FleetThemeResolver.resolve({ true }) { row(version = 3L) }.palette)
        assertEquals(BuiltInTheme.NEON, FleetThemeResolver.resolve({ true }) { null }.palette)
        assertEquals(BuiltInTheme.NEON, FleetThemeResolver.resolve({ true }) { throw IllegalStateException() }.palette)
        assertEquals(BuiltInTheme.NEON, FleetThemeResolver.resolve({ throw RuntimeException() }) { row() }.palette)
    }

    @Test fun appliesFleetThemeWhenPinnedAndValid() {
        val s = FleetThemeResolver.resolve({ true }) { row() }
        assertEquals(ThemeSource.FLEET, s.source)
    }

    @Test fun pinRequiresPackageAndCert() {
        val good = FleetThemePins.VICTORLAUNCH_DEBUG.replace(":", "").lowercase()
        assertTrue(FleetThemePins.isTrusted("com.victorious.launch", listOf(good)))
        assertTrue(FleetThemePins.isTrusted("com.victorious.launch", listOf("00", FleetThemePins.VICTORLAUNCH_DEBUG)))
        assertFalse(FleetThemePins.isTrusted("com.evil.launch", listOf(good)))
        assertFalse(FleetThemePins.isTrusted("com.victorious.launch", listOf("ab".repeat(32))))
        assertFalse(FleetThemePins.isTrusted("com.victorious.launch", emptyList()))
        assertFalse(FleetThemePins.isTrusted(null, listOf(good)))
    }
}
