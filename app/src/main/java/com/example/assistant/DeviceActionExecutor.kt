package com.example.assistant

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import com.example.util.ParsedDeviceAction
import kotlin.math.roundToInt

data class DeviceActionOutcome(
    val success: Boolean,
    val summary: String,
    val missingSystemPermission: String? = null
)

object DeviceActionExecutor {

    @Volatile
    private var torchEnabledState = false

    fun executeAll(context: Context, actions: List<ParsedDeviceAction>): List<DeviceActionOutcome> {
        if (actions.isEmpty()) return emptyList()
        return actions.map { execute(context, it) }
    }

    fun execute(context: Context, cmd: ParsedDeviceAction): DeviceActionOutcome {
        val action = cmd.action.trim().lowercase()
        val p1 = cmd.param1.trim()
        val p2 = cmd.param2.trim()

        return when (action) {
            "open_app", "launch_app" -> openApp(context, p1)
            "volume" -> adjustVolume(context, p1, p2)
            "brightness" -> setBrightness(context, p1)
            "screen_timeout", "timeout" -> setScreenTimeout(context, p1)
            "auto_rotate", "rotation", "rotate" -> setAutoRotate(context, p1)
            "ringer", "sound_mode" -> setRingerMode(context, p1)
            "dnd", "do_not_disturb" -> setDndMode(context, p1)
            "flashlight", "torch" -> setFlashlight(context, p1)
            "open_settings", "settings" -> openSettingsPage(context, p1)
            "wifi" -> openWifiControl(context, p1)
            "bluetooth" -> openBluetoothControl(context, p1)
            "nav", "navigate" -> performNavAction(context, p1)
            "tap", "click", "press" -> performTap(context, p1)
            "scroll", "swipe" -> performScroll(context, p1)
            "type", "input" -> performType(context, p1)
            "read_device" -> {
                val snap = DeviceInspector.readDeviceSnapshot(context)
                DeviceActionOutcome(
                    success = true,
                    summary = "${snap.manufacturer} ${snap.model} • Battery ${snap.batteryPercent}% • Vol ${snap.mediaVolumePct}% • Brightness ${snap.brightnessPct}%"
                )
            }
            else -> DeviceActionOutcome(false, "Unsupported device action: ${cmd.action}")
        }
    }

    fun openApp(context: Context, requestedName: String): DeviceActionOutcome {
        val query = requestedName.trim().lowercase()
        if (query.isEmpty()) return DeviceActionOutcome(false, "No app name specified.")

        // Handle explicit built-in intents first if requested directly
        when (query) {
            "camera" -> {
                val camIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (camIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(camIntent)
                    return DeviceActionOutcome(true, "Opened Camera")
                }
            }
            "alarm", "alarms", "timer" -> {
                val alarmIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (alarmIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(alarmIntent)
                    return DeviceActionOutcome(true, "Opened Clock / Alarms")
                }
            }
            "settings", "system settings" -> return openSettingsPage(context, "main")
        }

        val apps = DeviceInspector.listLaunchableApps(context)
        val matched = apps.firstOrNull { it.label.equals(query, ignoreCase = true) }
            ?: apps.firstOrNull { it.label.startsWith(query, ignoreCase = true) }
            ?: apps.firstOrNull { it.label.contains(query, ignoreCase = true) }
            ?: apps.firstOrNull { it.packageName.contains(query.replace(" ", ""), ignoreCase = true) }

        if (matched == null) {
            val refreshed = DeviceInspector.listLaunchableApps(context, forceRefresh = true)
            val retry = refreshed.firstOrNull {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query.replace(" ", ""), ignoreCase = true)
            } ?: return DeviceActionOutcome(false, "Could not find an installed app named \"$requestedName\".")
            return launchPackage(context, retry)
        }

        return launchPackage(context, matched)
    }

    private fun launchPackage(context: Context, app: InstalledAppInfo): DeviceActionOutcome {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                DeviceActionOutcome(true, "Opened ${app.label}")
            } else {
                DeviceActionOutcome(false, "${app.label} cannot be launched directly.")
            }
        } catch (e: Exception) {
            DeviceActionOutcome(false, "Failed to open ${app.label}: ${e.localizedMessage}")
        }
    }

    private fun adjustVolume(context: Context, levelOrDir: String, streamHint: String): DeviceActionOutcome {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = when (streamHint.lowercase()) {
            "ring", "ringtone", "ringer" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "notification", "notif" -> AudioManager.STREAM_NOTIFICATION
            "call", "voice" -> AudioManager.STREAM_VOICE_CALL
            else -> AudioManager.STREAM_MUSIC
        }
        val streamLabel = when (stream) {
            AudioManager.STREAM_RING -> "Ring"
            AudioManager.STREAM_ALARM -> "Alarm"
            AudioManager.STREAM_NOTIFICATION -> "Notification"
            AudioManager.STREAM_VOICE_CALL -> "Call"
            else -> "Media"
        }

        return try {
            val arg = levelOrDir.lowercase()
            val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
            when (arg) {
                "up", "increase", "higher", "louder" -> {
                    val step = (max * 0.15f).roundToInt().coerceAtLeast(1)
                    val target = (audio.getStreamVolume(stream) + step).coerceAtMost(max)
                    audio.setStreamVolume(stream, target, AudioManager.FLAG_SHOW_UI)
                    val pct = ((target.toFloat() / max) * 100f).roundToInt()
                    DeviceActionOutcome(true, "$streamLabel volume up to $pct%")
                }
                "down", "decrease", "lower", "quieter" -> {
                    val step = (max * 0.15f).roundToInt().coerceAtLeast(1)
                    val target = (audio.getStreamVolume(stream) - step).coerceAtLeast(0)
                    audio.setStreamVolume(stream, target, AudioManager.FLAG_SHOW_UI)
                    val pct = ((target.toFloat() / max) * 100f).roundToInt()
                    DeviceActionOutcome(true, "$streamLabel volume down to $pct%")
                }
                "mute", "silent", "0" -> {
                    audio.setStreamVolume(stream, 0, AudioManager.FLAG_SHOW_UI)
                    DeviceActionOutcome(true, "$streamLabel volume muted")
                }
                "unmute" -> {
                    val current = audio.getStreamVolume(stream)
                    val target = if (current == 0) (max * 0.5f).roundToInt().coerceAtLeast(1) else current
                    audio.setStreamVolume(stream, target, AudioManager.FLAG_SHOW_UI)
                    val pct = ((target.toFloat() / max) * 100f).roundToInt()
                    DeviceActionOutcome(true, "$streamLabel volume set to $pct%")
                }
                "max", "full" -> {
                    audio.setStreamVolume(stream, max, AudioManager.FLAG_SHOW_UI)
                    DeviceActionOutcome(true, "$streamLabel volume at 100%")
                }
                else -> {
                    val parsedPct = arg.removeSuffix("%").toIntOrNull()
                        ?: return DeviceActionOutcome(false, "Invalid volume value: $levelOrDir")
                    val clamped = parsedPct.coerceIn(0, 100)
                    val target = ((clamped / 100f) * max).roundToInt().coerceIn(0, max)
                    audio.setStreamVolume(stream, target, AudioManager.FLAG_SHOW_UI)
                    DeviceActionOutcome(true, "$streamLabel volume set to $clamped%")
                }
            }
        } catch (e: SecurityException) {
            DeviceActionOutcome(
                false,
                "Do Not Disturb access is needed to change $streamLabel volume.",
                missingSystemPermission = "dnd"
            )
        } catch (e: Exception) {
            DeviceActionOutcome(false, "Could not change volume: ${e.localizedMessage}")
        }
    }

    private fun setBrightness(context: Context, value: String): DeviceActionOutcome {
        if (!Settings.System.canWrite(context)) {
            openSettingsPage(context, "write_settings")
            return DeviceActionOutcome(
                false,
                "Please allow 'Modify system settings' so Ann can adjust brightness.",
                missingSystemPermission = "write_settings"
            )
        }
        val arg = value.lowercase().trim()
        val resolver = context.contentResolver
        return try {
            if (arg == "auto" || arg == "automatic") {
                Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                )
                return DeviceActionOutcome(true, "Auto-brightness enabled")
            }

            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            if (arg == "manual") {
                return DeviceActionOutcome(true, "Manual brightness enabled")
            }

            val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            val targetRaw = when (arg) {
                "up", "higher", "increase" -> (current + 45).coerceAtMost(255)
                "down", "lower", "decrease" -> (current - 45).coerceAtLeast(10)
                "max", "full" -> 255
                "min", "low" -> 20
                else -> {
                    val pct = arg.removeSuffix("%").toIntOrNull()?.coerceIn(2, 100)
                        ?: return DeviceActionOutcome(false, "Invalid brightness value: $value")
                    ((pct / 100f) * 255f).roundToInt().coerceIn(5, 255)
                }
            }
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, targetRaw)
            val pct = ((targetRaw / 255f) * 100f).roundToInt()
            DeviceActionOutcome(true, "Brightness set to $pct%")
        } catch (e: Exception) {
            DeviceActionOutcome(false, "Failed to update brightness: ${e.localizedMessage}")
        }
    }

    private fun setScreenTimeout(context: Context, value: String): DeviceActionOutcome {
        if (!Settings.System.canWrite(context)) {
            openSettingsPage(context, "write_settings")
            return DeviceActionOutcome(
                false,
                "Please allow 'Modify system settings' to change screen timeout.",
                missingSystemPermission = "write_settings"
            )
        }
        val seconds = value.lowercase().removeSuffix("s").removeSuffix("sec").trim().toIntOrNull()
            ?: return DeviceActionOutcome(false, "Invalid timeout seconds: $value")
        val clampedSec = seconds.coerceIn(15, 1800)
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                clampedSec * 1000
            )
            DeviceActionOutcome(true, "Screen timeout set to ${clampedSec}s")
        } catch (e: Exception) {
            DeviceActionOutcome(false, "Could not update screen timeout: ${e.localizedMessage}")
        }
    }

    private fun setAutoRotate(context: Context, value: String): DeviceActionOutcome {
        if (!Settings.System.canWrite(context)) {
            openSettingsPage(context, "write_settings")
            return DeviceActionOutcome(
                false,
                "Please allow 'Modify system settings' to toggle auto-rotate.",
                missingSystemPermission = "write_settings"
            )
        }
        val enable = value.lowercase() in setOf("on", "true", "enable", "enabled", "1")
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (enable) 1 else 0
            )
            DeviceActionOutcome(true, if (enable) "Auto-rotate turned on" else "Auto-rotate turned off")
        } catch (e: Exception) {
            DeviceActionOutcome(false, "Could not update auto-rotate: ${e.localizedMessage}")
        }
    }

    private fun setRingerMode(context: Context, mode: String): DeviceActionOutcome {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val arg = mode.lowercase()
        return when (arg) {
            "silent", "mute" -> {
                try {
                    audio.ringerMode = AudioManager.RINGER_MODE_SILENT
                    DeviceActionOutcome(true, "Phone set to Silent")
                } catch (_: SecurityException) {
                    // Android restricts RINGER_MODE_SILENT without Notification Policy; mute ring & notification streams directly instead
                    runCatching { audio.setStreamVolume(AudioManager.STREAM_RING, 0, 0) }
                    runCatching { audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0) }
                    runCatching { audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE }
                    DeviceActionOutcome(true, "Muted ringtone & notifications")
                } catch (e: Exception) {
                    DeviceActionOutcome(false, "Could not set silent mode: ${e.localizedMessage}")
                }
            }
            "vibrate" -> {
                try {
                    audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    DeviceActionOutcome(true, "Phone set to Vibrate")
                } catch (e: Exception) {
                    DeviceActionOutcome(false, "Could not switch to Vibrate: ${e.localizedMessage}")
                }
            }
            else -> {
                try {
                    audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    DeviceActionOutcome(true, "Phone ringer set to Normal")
                } catch (_: SecurityException) {
                    val maxRing = audio.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)
                    runCatching { audio.setStreamVolume(AudioManager.STREAM_RING, (maxRing * 0.6f).roundToInt().coerceAtLeast(1), AudioManager.FLAG_SHOW_UI) }
                    DeviceActionOutcome(true, "Restored ringtone sound")
                } catch (e: Exception) {
                    DeviceActionOutcome(false, "Could not set normal ringer: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun setDndMode(context: Context, value: String): DeviceActionOutcome {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val enable = value.lowercase() in setOf("on", "true", "enable", "enabled")
        if (nm.isNotificationPolicyAccessGranted) {
            return try {
                nm.setInterruptionFilter(
                    if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    else NotificationManager.INTERRUPTION_FILTER_ALL
                )
                DeviceActionOutcome(true, if (enable) "Do Not Disturb turned on" else "Do Not Disturb turned off")
            } catch (e: Exception) {
                DeviceActionOutcome(false, "Could not change Do Not Disturb: ${e.localizedMessage}")
            }
        }
        // Gracefully control sound/vibration directly instead of nagging with an extra DND permission gate
        return try {
            if (enable) {
                runCatching { audio.setStreamVolume(AudioManager.STREAM_RING, 0, 0) }
                runCatching { audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0) }
                runCatching { audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE }
                DeviceActionOutcome(true, "Silenced ring & notifications")
            } else {
                runCatching { audio.ringerMode = AudioManager.RINGER_MODE_NORMAL }
                DeviceActionOutcome(true, "Restored normal notification sound")
            }
        } catch (e: Exception) {
            DeviceActionOutcome(false, "Could not update sound state: ${e.localizedMessage}")
        }
    }

    private fun setFlashlight(context: Context, value: String): DeviceActionOutcome {
        return try {
            val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = camManager.cameraIdList.firstOrNull { id ->
                val chars = camManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return DeviceActionOutcome(false, "No camera flashlight found on this device.")

            val target = when (value.lowercase()) {
                "on", "true", "enable" -> true
                "off", "false", "disable" -> false
                else -> !torchEnabledState
            }
            camManager.setTorchMode(cameraId, target)
            torchEnabledState = target
            DeviceActionOutcome(true, if (target) "Flashlight turned on" else "Flashlight turned off")
        } catch (e: Exception) {
            DeviceActionOutcome(false, "Could not toggle flashlight: ${e.localizedMessage}")
        }
    }

    private fun openWifiControl(context: Context, desired: String): DeviceActionOutcome {
        val panelIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(panelIntent)
            val note = if (desired.isNotBlank()) " (Android restricts apps from toggling Wi-Fi silently; opened panel)" else ""
            DeviceActionOutcome(true, "Opened Internet / Wi-Fi controls$note")
        } catch (_: Exception) {
            openSettingsPage(context, "wifi")
        }
    }

    private fun openBluetoothControl(context: Context, desired: String): DeviceActionOutcome {
        val res = openSettingsPage(context, "bluetooth")
        return if (res.success && desired.isNotBlank()) {
            res.copy(summary = "Opened Bluetooth settings (tap switch to turn $desired)")
        } else {
            res
        }
    }

    fun createAccessibilitySettingsIntent(context: Context): Intent {
        val serviceId = "${context.packageName}/${AnnAccessibilityService::class.java.name}"
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            // Hints Android / OEM Settings to highlight Ann Agentic Assistant automatically
            putExtra(":settings:fragment_args_key", serviceId)
            val bundle = android.os.Bundle().apply {
                putString(":settings:fragment_args_key", serviceId)
            }
            putExtra(":settings:show_fragment_args", bundle)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun openSettingsPage(context: Context, section: String): DeviceActionOutcome {
        val norm = section.trim().lowercase()
        val intent = when (norm) {
            "wifi", "wi-fi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "internet", "network", "data" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                else Intent(Settings.ACTION_WIRELESS_SETTINGS)
            }
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "display", "brightness", "screen" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "sound", "audio" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            "volume" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_VOLUME)
                else Intent(Settings.ACTION_SOUND_SETTINGS)
            }
            "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "location", "gps" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "apps", "applications" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
            "accessibility" -> createAccessibilitySettingsIntent(context)
            "overlay", "draw_over" -> Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.fromParts("package", context.packageName, null)
            )
            "write_settings", "modify_settings" -> Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
            "dnd", "notification_policy" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            DeviceActionOutcome(true, "Opened ${norm.ifEmpty { "system" }} settings")
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
                DeviceActionOutcome(true, "Opened Settings")
            } catch (e: Exception) {
                DeviceActionOutcome(false, "Could not open settings: ${e.localizedMessage}")
            }
        }
    }

    private fun performNavAction(context: Context, actionName: String): DeviceActionOutcome {
        val svc = AnnAccessibilityService.instance
            ?: return DeviceActionOutcome(
                false,
                "Turn on 'Ann Agentic Assistant' under Permissions to press $actionName.",
                missingSystemPermission = "accessibility"
            )
        val (ok, msg) = svc.performGlobal(actionName)
        return DeviceActionOutcome(ok, msg)
    }

    private fun performTap(context: Context, target: String): DeviceActionOutcome {
        val svc = AnnAccessibilityService.instance
            ?: return DeviceActionOutcome(
                false,
                "Turn on 'Ann Agentic Assistant' under Permissions to tap \"$target\".",
                missingSystemPermission = "accessibility"
            )
        val (ok, msg) = svc.tapMatchingText(target)
        return DeviceActionOutcome(ok, msg)
    }

    private fun performScroll(context: Context, direction: String): DeviceActionOutcome {
        val svc = AnnAccessibilityService.instance
            ?: return DeviceActionOutcome(
                false,
                "Turn on 'Ann Agentic Assistant' under Permissions to scroll screens.",
                missingSystemPermission = "accessibility"
            )
        val (ok, msg) = svc.scrollDirection(direction)
        return DeviceActionOutcome(ok, msg)
    }

    private fun performType(context: Context, text: String): DeviceActionOutcome {
        val svc = AnnAccessibilityService.instance
            ?: return DeviceActionOutcome(
                false,
                "Turn on 'Ann Agentic Assistant' under Permissions to type on screen.",
                missingSystemPermission = "accessibility"
            )
        val (ok, msg) = svc.typeIntoFocusedInput(text)
        return DeviceActionOutcome(ok, msg)
    }
}
