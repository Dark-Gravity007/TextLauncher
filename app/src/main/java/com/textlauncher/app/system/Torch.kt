package com.textlauncher.app.system

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

/** Flashlight on/off. Needs no permission; follows the real torch state so it stays in sync with the quick-settings tile. */
class Torch(context: Context) {

    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val cameraId: String? = try {
        manager.cameraIdList.firstOrNull { id ->
            val c = manager.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    } catch (e: Exception) {
        null
    }

    @Volatile
    private var on = false

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) on = enabled
        }
    }

    private var registered = false

    init {
        if (cameraId != null) {
            try {
                manager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
                registered = true
            } catch (e: Exception) {
                registered = false
            }
        }
    }

    /** Returns false if this device has no usable flash. */
    fun toggle(): Boolean {
        val id = cameraId ?: return false
        return try {
            manager.setTorchMode(id, !on)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun release() {
        if (registered) {
            try {
                manager.unregisterTorchCallback(callback)
            } catch (e: Exception) {
                // ignore
            }
            registered = false
        }
    }
}
