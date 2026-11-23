package com.example.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.ui.theme.MyApplicationTheme

private class OverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

class AnnAssistantService : Service() {

    companion object {
        const val ACTION_START = "com.example.assistant.START"
        const val ACTION_STOP = "com.example.assistant.STOP"
        const val EXTRA_AUTO_LISTEN = "extra_auto_listen"

        private const val CHANNEL_ID = "ann_assistant_overlay_channel"
        private const val NOTIFICATION_ID = 4107
    }

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AnnAssistantEngine.ensureInitialized(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundNotification()
        val autoListen = intent?.getBooleanExtra(EXTRA_AUTO_LISTEN, true) ?: true

        if (Settings.canDrawOverlays(this)) {
            attachQuickBallOverlay()
        }
        AnnAssistantEngine.onServiceStarted(autoListen = autoListen)
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ann Voice Assistant QuickBall",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps Ann floating QuickBall and always-on silent voice detection ready."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AnnAssistantService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Ann Assistant is Active")
            .setContentText("QuickBall overlay & always-on voice listener active")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "Stop", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun attachQuickBallOverlay() {
        if (overlayView != null) return

        val owner = OverlayLifecycleOwner().apply { onCreate() }
        overlayLifecycleOwner = owner

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val dm = resources.displayMetrics
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (14 * dm.density).toInt()
            y = (dm.heightPixels * 0.28f).toInt()
        }
        overlayParams = params

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                MyApplicationTheme {
                    val state by AnnAssistantEngine.state.collectAsState()
                    QuickBallOverlayContent(
                        state = state,
                        onDragDelta = { dx, dy ->
                            val lp = overlayParams ?: return@QuickBallOverlayContent
                            // Gravity is TOP | END so positive dx moves left (subtract from x)
                            lp.x = (lp.x - dx.toInt()).coerceAtLeast(0)
                            lp.y = (lp.y + dy.toInt()).coerceAtLeast(0)
                            runCatching { windowManager?.updateViewLayout(this, lp) }
                        },
                        onTapOrb = { AnnAssistantEngine.toggleExpanded() },
                        onLongPressOrb = { AnnAssistantEngine.toggleListeningNow() },
                        onToggleListen = { AnnAssistantEngine.toggleListeningNow() },
                        onToggleTts = { AnnAssistantEngine.toggleTts() },
                        onToggleHandsFreeLoop = { AnnAssistantEngine.toggleHandsFreeLoop() },
                        onQuickAction = { action, p1 -> AnnAssistantEngine.executeQuickAction(action, p1) },
                        onOpenSettingsTarget = { target ->
                            DeviceActionExecutor.openSettingsPage(this@AnnAssistantService, target)
                        },
                        onOpenAnnApp = {
                            val launch = Intent(this@AnnAssistantService, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            startActivity(launch)
                        },
                        onMinimizePanel = { AnnAssistantEngine.setExpanded(false) },
                        onStopService = { AnnAssistantEngine.stopAssistantOverlay(this@AnnAssistantService) }
                    )
                }
            }
        }

        overlayView = view
        runCatching { windowManager?.addView(view, params) }
    }

    override fun onDestroy() {
        overlayView?.let { view ->
            runCatching { windowManager?.removeViewImmediate(view) }
        }
        overlayView = null
        overlayLifecycleOwner?.onDestroy()
        overlayLifecycleOwner = null
        AnnAssistantEngine.onServiceDestroyed()
        super.onDestroy()
    }
}
