package com.victorious.taskbar.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class BatteryInfo(val percent: Int, val charging: Boolean, val tempC: Float?)

/**
 * Tray controls that need no special permission: torch (CameraManager.setTorchMode needs no
 * CAMERA permission), media keys and volume (AudioManager), battery (sticky broadcast).
 */
class DeviceControls(private val context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val camera = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val torchId: String? = try {
        camera.cameraIdList.firstOrNull {
            camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (e: Exception) { null }

    private val _torchOn = MutableStateFlow(false)
    val torchOn: StateFlow<Boolean> = _torchOn
    val hasTorch: Boolean get() = torchId != null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) _torchOn.value = enabled
        }
    }

    fun start() {
        if (torchId != null) try { camera.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper())) } catch (_: Exception) {}
    }

    fun stop() {
        try { camera.unregisterTorchCallback(torchCallback) } catch (_: Exception) {}
    }

    fun toggleTorch(): Boolean {
        val id = torchId ?: return false
        return try { camera.setTorchMode(id, !_torchOn.value); true } catch (e: Exception) { false }
    }

    fun mediaKey(code: Int) {
        val t = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }

    fun volume(direction: Int) =
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)

    fun toggleMute() = volume(AudioManager.ADJUST_TOGGLE_MUTE)

    fun isMusicActive(): Boolean = audio.isMusicActive

    fun battery(): BatteryInfo {
        val i: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temp = i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return BatteryInfo(
            percent = if (level < 0 || scale <= 0) -1 else level * 100 / scale,
            charging = charging,
            tempC = if (temp == Int.MIN_VALUE) null else temp / 10f,
        )
    }
}
