package com.victorious.taskbar.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Read-only client for VictorLaunch's fleet theme provider.
 * Only ever calls `query()`; never insert/update/delete.
 */
object FleetThemeClient {
    private val uri: Uri = Uri.parse(FleetThemeContract.URI)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(BuiltInTheme.state("not loaded yet"))
    val state: StateFlow<FleetThemeState> = _state.asStateFlow()

    @Volatile private var observer: ContentObserver? = null

    /** Idempotent: query once and register a ContentObserver. */
    fun start(context: Context) {
        val app = context.applicationContext
        if (observer == null) {
            synchronized(this) {
                if (observer == null) {
                    val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) = refresh(app)
                    }
                    try {
                        app.contentResolver.registerContentObserver(uri, false, obs)
                    } catch (e: SecurityException) {
                        // Observer registration can be refused; the broadcast still wakes us.
                    }
                    observer = obs
                }
            }
        }
        refresh(app)
    }

    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch {
            _state.value = FleetThemeResolver.resolve(
                pinCheck = { isProviderPinned(app) },
                query = { queryRow(app) },
            )
        }
    }

    /** Resolve the authority to its package and check package + signing-cert SHA-256 against the pins. */
    internal fun isProviderPinned(context: Context): Boolean {
        val pm = context.packageManager
        val info = pm.resolveContentProvider(FleetThemeContract.AUTHORITY, 0) ?: return false
        val pkg = info.packageName
        return FleetThemePins.isTrusted(pkg, signingCertsSha256(pm, pkg))
    }

    @Suppress("DEPRECATION")
    private fun signingCertsSha256(pm: PackageManager, pkg: String): List<String> {
        val sigs = try {
            if (Build.VERSION.SDK_INT >= 28) {
                val si = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: return emptyList()
                // current signer(s) only; older rotated certs in the lineage are not trusted
                si.apkContentsSigners
            } else {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
            }
        } catch (e: PackageManager.NameNotFoundException) {
            return emptyList()
        } ?: return emptyList()
        val md = MessageDigest.getInstance("SHA-256")
        return sigs.map { s -> md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }
    }

    private fun queryRow(context: Context): Map<String, Any?>? {
        val c: Cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
        c.use {
            if (!it.moveToFirst()) return null
            val row = HashMap<String, Any?>()
            for (i in 0 until it.columnCount) {
                row[it.getColumnName(i)] = when (it.getType(i)) {
                    Cursor.FIELD_TYPE_INTEGER -> it.getLong(i)
                    Cursor.FIELD_TYPE_STRING -> it.getString(i)
                    else -> null
                }
            }
            return row
        }
    }
}

/**
 * Exported (VictorLaunch sends it explicitly to our package and is signed with a different key,
 * so it cannot hold a signature permission of ours). Treated purely as a wake-up: extras are
 * never used as colors or as auth. We re-query, and the query path re-checks the provider's
 * package + cert pin.
 */
class FleetThemeChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != FleetThemeContract.ACTION_CHANGED) return
        FleetThemeClient.refresh(context)
    }
}
