package com.example.assistant

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.util.FileHelper

data class InstalledAppInfo(
    val label: String,
    val packageName: String
)

data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val androidVersion: String,
    val sdkInt: Int,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val ramAvailableMb: Long,
    val ramTotalMb: Long,
    val storageFreeGb: Double,
    val storageTotalGb: Double,
    val mediaVolumePct: Int,
    val ringVolumePct: Int,
    val alarmVolumePct: Int,
    val ringerMode: String,
    val dndEnabled: Boolean,
    val brightnessPct: Int,
    val autoBrightness: Boolean,
    val screenTimeoutSec: Int,
    val autoRotate: Boolean,
    val wifiConnected: Boolean,
    val airplaneMode: Boolean,
    val canDrawOverlays: Boolean,
    val canWriteSettings: Boolean,
    val isDndPolicyGranted: Boolean,
    val isAccessibilityEnabled: Boolean,
    val hasMicPermission: Boolean,
    val hasContactsPermission: Boolean,
    val hasAllFilesAccess: Boolean,
    val screenSnapshot: ScreenSnapshot?
)

object DeviceInspector {

    @Volatile
    private var cachedApps: List<InstalledAppInfo>? = null

    fun listLaunchableApps(context: Context, forceRefresh: Boolean = false): List<InstalledAppInfo> {
        if (!forceRefresh) {
            cachedApps?.let { return it }
        }
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
        } catch (_: Exception) {
            emptyList()
        }

        val list = resolved
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isEmpty()) null else InstalledAppInfo(label = label, packageName = pkg)
            }
            .distinctBy { "${it.label.lowercase()}|${it.packageName}" }
            .sortedBy { it.label.lowercase() }

        cachedApps = list
        return list
    }

    fun readDeviceSnapshot(context: Context): DeviceSnapshot {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val ramAvailMb = memInfo.availMem / (1024L * 1024L)
        val ramTotalMb = memInfo.totalMem / (1024L * 1024L)

        val stat = StatFs(Environment.getDataDirectory().path)
        val freeGb = stat.availableBytes / (1024.0 * 1024.0 * 1024.0)
        val totalGb = stat.totalBytes / (1024.0 * 1024.0 * 1024.0)

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        fun pctFor(stream: Int): Int {
            val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
            val cur = audio.getStreamVolume(stream)
            return ((cur.toFloat() / max.toFloat()) * 100f).toInt().coerceIn(0, 100)
        }
        val ringerStr = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "silent"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            else -> "normal"
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dndEnabled = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

        val rawBrightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        val brightnessPct = ((rawBrightness.coerceIn(0, 255) / 255f) * 100f).toInt()
        val autoBrightness = Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        val timeoutMs = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)
        val autoRotate = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeCaps = cm?.getNetworkCapabilities(cm.activeNetwork)
        val wifiConnected = activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val airplaneMode = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

        val accessibility = AnnAccessibilityService.instance
        val screenSnap = accessibility?.readScreenSnapshot()

        return DeviceSnapshot(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            brand = Build.BRAND,
            androidVersion = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
            sdkInt = Build.VERSION.SDK_INT,
            batteryPercent = batteryPct,
            isCharging = isCharging,
            ramAvailableMb = ramAvailMb,
            ramTotalMb = ramTotalMb,
            storageFreeGb = freeGb,
            storageTotalGb = totalGb,
            mediaVolumePct = pctFor(AudioManager.STREAM_MUSIC),
            ringVolumePct = pctFor(AudioManager.STREAM_RING),
            alarmVolumePct = pctFor(AudioManager.STREAM_ALARM),
            ringerMode = ringerStr,
            dndEnabled = dndEnabled,
            brightnessPct = brightnessPct,
            autoBrightness = autoBrightness,
            screenTimeoutSec = (timeoutMs / 1000).coerceAtLeast(1),
            autoRotate = autoRotate,
            wifiConnected = wifiConnected,
            airplaneMode = airplaneMode,
            canDrawOverlays = Settings.canDrawOverlays(context),
            canWriteSettings = Settings.System.canWrite(context),
            isDndPolicyGranted = nm.isNotificationPolicyAccessGranted,
            isAccessibilityEnabled = AnnAccessibilityService.isEnabled(context),
            hasMicPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED,
            hasContactsPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED,
            hasAllFilesAccess = FileHelper.hasAllFilesAccess(context),
            screenSnapshot = screenSnap
        )
    }

    fun formatForPrompt(context: Context, snapshot: DeviceSnapshot): String {
        val apps = listLaunchableApps(context)
        val appNamesSample = apps
            .filter { it.packageName != context.packageName }
            .take(45)
            .joinToString(", ") { it.label }

        val screenBlock = snapshot.screenSnapshot?.let { screen ->
            val labels = screen.visibleElements.joinToString(" | ") { el ->
                val tag = buildString {
                    if (el.clickable) append("[tap]")
                    if (el.editable) append("[input]")
                }
                if (tag.isNotEmpty()) "${el.text}$tag" else el.text
            }
            "Foreground App Package: ${screen.foregroundPackage.ifBlank { "unknown" }}\nVisible Screen Elements: ${labels.ifBlank { "None readable" }}"
        } ?: "Screen Accessibility Control: Not enabled (can only open apps & write system settings)"

        return buildString {
            appendLine("Device: ${snapshot.manufacturer} ${snapshot.model} (Android ${snapshot.androidVersion}, API ${snapshot.sdkInt})")
            appendLine(
                "Battery: ${snapshot.batteryPercent}% (${if (snapshot.isCharging) "charging" else "on battery"}) | " +
                    "RAM free: ${snapshot.ramAvailableMb}/${snapshot.ramTotalMb} MB | " +
                    "Storage free: ${"%.1f".format(snapshot.storageFreeGb)}/${"%.1f".format(snapshot.storageTotalGb)} GB"
            )
            appendLine(
                "Audio: Media ${snapshot.mediaVolumePct}%, Ring ${snapshot.ringVolumePct}%, Alarm ${snapshot.alarmVolumePct}% | " +
                    "Ringer: ${snapshot.ringerMode} | DND: ${if (snapshot.dndEnabled) "on" else "off"}"
            )
            appendLine(
                "Display & System: Brightness ${snapshot.brightnessPct}% (auto=${snapshot.autoBrightness}), " +
                    "Timeout ${snapshot.screenTimeoutSec}s, AutoRotate=${snapshot.autoRotate}, " +
                    "WiFi=${snapshot.wifiConnected}, Airplane=${snapshot.airplaneMode}"
            )
            appendLine(
                "Granted Control Permissions: WriteSettings=${snapshot.canWriteSettings}, " +
                    "OverlayQuickBall=${snapshot.canDrawOverlays}, DndPolicy=${snapshot.isDndPolicyGranted}, " +
                    "AccessibilityHandControl=${snapshot.isAccessibilityEnabled}, AllFiles=${snapshot.hasAllFilesAccess}"
            )
            appendLine(screenBlock)
            if (appNamesSample.isNotEmpty()) {
                appendLine("Installed Apps Sample (${apps.size} total): $appNamesSample")
            }
        }.trim()
    }
}
