package com.victorious.taskbar.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Read-only client side of the VictorLaunch fleet theme contract
 * (victorlaunch docs/specs/FLEET_THEME.md, schemaVersion 1).
 * Pure Kotlin so it is unit-testable on the JVM.
 */
object FleetThemeContract {
    const val SCHEMA_VERSION = 1
    const val PROVIDER_PACKAGE = "com.victorious.launch"
    const val AUTHORITY = "com.victorious.launch.theme"
    const val PATH_CURRENT = "current"
    const val URI = "content://$AUTHORITY/$PATH_CURRENT"
    const val ACTION_CHANGED = "com.victorious.launch.action.FLEET_THEME_CHANGED"
    const val EXTRA_SCHEMA_VERSION = "schemaVersion"

    const val COL_SCHEMA_VERSION = "schemaVersion"
    const val COL_MODE = "mode"
    const val COL_ICON_STYLE = "iconStyle"
    const val COL_ICON_PACK = "iconPackPackage"
    const val COL_JSON = "json"
    const val COL_UPDATED_AT = "updatedAtMs"
    val PALETTE_COLUMNS = listOf(
        "background", "surface", "primary", "secondary", "onBackground", "muted", "danger", "stale", "tile",
    )
}

/**
 * Signing-cert pins for the provider package. Mirrors VictorLaunch
 * `FleetPins.VICTORLAUNCH_DEBUG` (victorlaunch keystore/README.md).
 * Release cert is not published yet; add it here when it is.
 */
object FleetThemePins {
    const val VICTORLAUNCH_DEBUG =
        "B9:9A:AF:E2:C1:41:8B:5D:64:AA:0D:4B:AC:61:FC:F7:A7:BA:81:44:35:E0:EF:89:E8:AA:55:BE:56:4E:4A:69"

    val KNOWN: Map<String, Set<String>> = mapOf(FleetThemeContract.PROVIDER_PACKAGE to setOf(VICTORLAUNCH_DEBUG))

    fun normalize(cert: String): String = cert.replace(":", "").replace(" ", "").lowercase()

    /** Both package AND one of its current signing certs must match a pin. */
    fun isTrusted(packageName: String?, certsSha256: List<String>): Boolean {
        if (packageName == null || packageName != FleetThemeContract.PROVIDER_PACKAGE) return false
        val pins = KNOWN[packageName].orEmpty().map(::normalize).toSet()
        return certsSha256.any { normalize(it) in pins }
    }
}

/** Colors as ARGB ints. */
data class FleetPalette(
    val background: Int,
    val surface: Int,
    val primary: Int,
    val secondary: Int,
    val onBackground: Int,
    val muted: Int,
    val danger: Int,
    val stale: Int,
    val tile: Int,
)

enum class ThemeSource { BUILT_IN, FLEET }

data class FleetThemeState(
    val palette: FleetPalette,
    val source: ThemeSource,
    val mode: String? = null,
    val updatedAtMs: Long = 0,
    /** Why we are on the built-in theme, if we are. */
    val fallbackReason: String? = null,
)

object BuiltInTheme {
    /** VictorTaskbar's own neon theme, used whenever the fleet theme is unavailable. */
    val NEON = FleetPalette(
        background = 0xFF0A0A1A.toInt(),
        surface = 0xFF16162A.toInt(),
        primary = 0xFF00E5FF.toInt(),
        secondary = 0xFFFF2BD6.toInt(),
        onBackground = 0xFFFFFFFF.toInt(),
        muted = 0xFFB0B3C0.toInt(),
        danger = 0xFFFF6B8A.toInt(),
        stale = 0xFFFFC14D.toInt(),
        tile = 0xE60A0A1A.toInt(),
    )

    fun state(reason: String?) = FleetThemeState(NEON, ThemeSource.BUILT_IN, fallbackReason = reason)
}

sealed class ParseResult {
    data class Ok(val state: FleetThemeState) : ParseResult()
    data class Rejected(val reason: String) : ParseResult()
}

object FleetThemeParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** `#RRGGBB` or `#AARRGGBB`; anything else is null. */
    fun parseHex(raw: String?): Int? {
        val h = raw?.trim()?.removePrefix("#") ?: return null
        if (h.length != 6 && h.length != 8) return null
        if (!h.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        val v = h.toLong(16)
        return if (h.length == 6) (0xFF000000L or v).toInt() else v.toInt()
    }

    private fun palette(get: (String) -> String?): FleetPalette? {
        val c = FleetThemeContract.PALETTE_COLUMNS.associateWith { parseHex(get(it)) ?: return null }
        return FleetPalette(
            c.getValue("background"), c.getValue("surface"), c.getValue("primary"), c.getValue("secondary"),
            c.getValue("onBackground"), c.getValue("muted"), c.getValue("danger"), c.getValue("stale"),
            c.getValue("tile"),
        )
    }

    fun parseJson(text: String): ParseResult {
        val obj: JsonObject = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            return ParseResult.Rejected("invalid json")
        }
        val version = runCatching { obj["schemaVersion"]?.jsonPrimitive?.intOrNull }.getOrNull()
        if (version != FleetThemeContract.SCHEMA_VERSION) return ParseResult.Rejected("unsupported schemaVersion $version")
        val pal = runCatching { obj["palette"]?.jsonObject }.getOrNull() ?: return ParseResult.Rejected("missing palette")
        val palette = palette { k -> runCatching { pal[k]?.jsonPrimitive?.contentOrNull }.getOrNull() }
            ?: return ParseResult.Rejected("bad palette color")
        return ParseResult.Ok(
            FleetThemeState(
                palette = palette,
                source = ThemeSource.FLEET,
                mode = runCatching { obj["mode"]?.jsonPrimitive?.contentOrNull }.getOrNull(),
                updatedAtMs = runCatching { obj["updatedAtMs"]?.jsonPrimitive?.longOrNull }.getOrNull() ?: 0,
            )
        )
    }

    /**
     * Parse one provider row given as column -> value. Unknown columns are ignored.
     * Prefers the `json` column; falls back to the flat palette columns.
     */
    fun parseRow(row: Map<String, Any?>): ParseResult {
        val version = (row[FleetThemeContract.COL_SCHEMA_VERSION] as? Number)?.toInt()
            ?: row[FleetThemeContract.COL_SCHEMA_VERSION]?.toString()?.toIntOrNull()
        if (version != FleetThemeContract.SCHEMA_VERSION) return ParseResult.Rejected("unsupported schemaVersion $version")
        val text = row[FleetThemeContract.COL_JSON] as? String
        if (!text.isNullOrBlank()) {
            val r = parseJson(text)
            if (r is ParseResult.Ok) return r
        }
        val palette = palette { row[it] as? String } ?: return ParseResult.Rejected("bad palette color")
        return ParseResult.Ok(
            FleetThemeState(
                palette = palette,
                source = ThemeSource.FLEET,
                mode = row[FleetThemeContract.COL_MODE] as? String,
                updatedAtMs = (row[FleetThemeContract.COL_UPDATED_AT] as? Number)?.toLong() ?: 0,
            )
        )
    }
}

/** Decides the theme to apply. Any failure means the built-in neon theme; colors are never invented. */
object FleetThemeResolver {
    fun resolve(pinCheck: () -> Boolean, query: () -> Map<String, Any?>?): FleetThemeState {
        val pinned = try { pinCheck() } catch (e: Exception) { false }
        if (!pinned) return BuiltInTheme.state("provider package/cert not pinned or not installed")
        val row = try {
            query()
        } catch (e: SecurityException) {
            return BuiltInTheme.state("VictorLaunch refused access (not trusted): pin VictorTaskbar in VictorLaunch")
        } catch (e: Exception) {
            return BuiltInTheme.state("query failed: ${e.javaClass.simpleName}")
        } ?: return BuiltInTheme.state("no theme row")
        return when (val r = FleetThemeParser.parseRow(row)) {
            is ParseResult.Ok -> r.state
            is ParseResult.Rejected -> BuiltInTheme.state(r.reason)
        }
    }
}
