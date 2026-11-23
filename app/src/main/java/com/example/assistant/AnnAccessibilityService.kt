package com.example.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

data class ScreenUiElement(
    val text: String,
    val clickable: Boolean,
    val editable: Boolean
)

data class ScreenSnapshot(
    val accessibilityEnabled: Boolean,
    val foregroundPackage: String,
    val visibleElements: List<ScreenUiElement>
)

class AnnAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AnnAccessibilityService? = null
            private set

        @Volatile
        var currentForegroundPackage: String = ""
            private set

        fun isEnabled(context: Context): Boolean {
            if (instance != null) return true
            return try {
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                val target = "${context.packageName}/${AnnAccessibilityService::class.java.name}"
                val shortTarget = "${context.packageName}/.assistant.AnnAccessibilityService"
                enabledServices.split(':').any {
                    it.equals(target, ignoreCase = true) || it.equals(shortTarget, ignoreCase = true)
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()?.takeIf { it.isNotBlank() }
        if (pkg != null) {
            currentForegroundPackage = pkg
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    fun readScreenSnapshot(maxItems: Int = 24): ScreenSnapshot {
        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return ScreenSnapshot(
            accessibilityEnabled = true,
            foregroundPackage = currentForegroundPackage,
            visibleElements = emptyList()
        )

        val pkg = root.packageName?.toString()?.takeIf { it.isNotBlank() } ?: currentForegroundPackage
        val elements = mutableListOf<ScreenUiElement>()
        val seen = HashSet<String>()

        fun traverse(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 14 || elements.size >= maxItems) return
            val label = sequenceOf(
                node.text?.toString(),
                node.contentDescription?.toString()
            )
                .mapNotNull { it?.trim() }
                .firstOrNull { it.isNotEmpty() }

            if (label != null) {
                val key = "${label.lowercase()}|${node.isClickable}|${node.isEditable}"
                if (seen.add(key)) {
                    elements.add(
                        ScreenUiElement(
                            text = label.take(64),
                            clickable = node.isClickable,
                            editable = node.isEditable
                        )
                    )
                }
            }
            val count = node.childCount.coerceAtMost(32)
            for (i in 0 until count) {
                traverse(node.getChild(i), depth + 1)
                if (elements.size >= maxItems) break
            }
        }

        traverse(root, 0)
        return ScreenSnapshot(
            accessibilityEnabled = true,
            foregroundPackage = pkg,
            visibleElements = elements
        )
    }

    fun performGlobal(actionName: String): Pair<Boolean, String> {
        val normalized = actionName.trim().lowercase()
        val action = when (normalized) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents", "recent", "overview" -> GLOBAL_ACTION_RECENTS
            "notifications", "notification" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings", "quicksettings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "power", "power_dialog" -> GLOBAL_ACTION_POWER_DIALOG
            "lock", "lock_screen" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GLOBAL_ACTION_LOCK_SCREEN
                else return false to "Lock screen requires Android 9 or newer."
            }
            "screenshot" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GLOBAL_ACTION_TAKE_SCREENSHOT
                else return false to "Screenshot requires Android 9 or newer."
            }
            else -> return false to "Unknown navigation action: $actionName"
        }
        val ok = performGlobalAction(action)
        return ok to if (ok) "Performed $normalized" else "Could not perform $normalized"
    }

    fun tapMatchingText(target: String): Pair<Boolean, String> {
        val query = target.trim()
        if (query.isEmpty()) return false to "Nothing specified to tap."
        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return false to "No active screen window detected."

        val exactNode = findNodeByText(root, query, exact = true)
            ?: findNodeByText(root, query, exact = false)
            ?: return false to "Could not find \"$query\" on the screen."

        var clickable: AccessibilityNodeInfo? = exactNode
        var hops = 0
        while (clickable != null && !clickable.isClickable && hops < 6) {
            clickable = clickable.parent
            hops++
        }

        if (clickable != null && clickable.isClickable && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true to "Tapped \"$query\""
        }

        val bounds = Rect()
        exactNode.getBoundsInScreen(bounds)
        if (!bounds.isEmpty && bounds.centerX() > 0 && bounds.centerY() > 0) {
            val dispatched = tapCoordinates(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            if (dispatched) return true to "Tapped \"$query\""
        }
        return false to "Found \"$query\" but could not click it."
    }

    fun tapCoordinates(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    fun scrollDirection(direction: String): Pair<Boolean, String> {
        val norm = direction.trim().lowercase()
        val dm = resources.displayMetrics
        val cx = dm.widthPixels * 0.5f
        val cy = dm.heightPixels * 0.5f

        val (startX, startY, endX, endY) = when (norm) {
            "down" -> listOf(cx, dm.heightPixels * 0.72f, cx, dm.heightPixels * 0.28f)
            "up" -> listOf(cx, dm.heightPixels * 0.30f, cx, dm.heightPixels * 0.74f)
            "left" -> listOf(dm.widthPixels * 0.80f, cy, dm.widthPixels * 0.20f, cy)
            "right" -> listOf(dm.widthPixels * 0.20f, cy, dm.widthPixels * 0.80f, cy)
            else -> return false to "Unknown scroll direction: $direction"
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 280L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = dispatchGesture(gesture, null, null)
        if (dispatched) return true to "Scrolled $norm"

        val root = rootInActiveWindow ?: return false to "Could not scroll."
        val scrollAction = if (norm == "up" || norm == "right") {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        val ok = findScrollableNode(root)?.performAction(scrollAction) == true
        return ok to if (ok) "Scrolled $norm" else "Screen cannot scroll $norm right now."
    }

    fun typeIntoFocusedInput(text: String): Pair<Boolean, String> {
        val root = rootInActiveWindow ?: return false to "No active screen to type into."
        val targetNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findFirstEditable(root)
            ?: return false to "No editable input field found on screen."

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return ok to if (ok) "Typed text into screen field" else "Could not type into active field."
    }

    private fun findNodeByText(
        node: AccessibilityNodeInfo?,
        query: String,
        exact: Boolean,
        depth: Int = 0
    ): AccessibilityNodeInfo? {
        if (node == null || depth > 16) return null
        val t = node.text?.toString()?.trim().orEmpty()
        val cd = node.contentDescription?.toString()?.trim().orEmpty()
        val matched = if (exact) {
            t.equals(query, ignoreCase = true) || cd.equals(query, ignoreCase = true)
        } else {
            t.contains(query, ignoreCase = true) || cd.contains(query, ignoreCase = true)
        }
        if (matched && node.isVisibleToUser) return node

        for (i in 0 until node.childCount) {
            val childMatch = findNodeByText(node.getChild(i), query, exact, depth + 1)
            if (childMatch != null) return childMatch
        }
        return null
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?, depth: Int = 0): AccessibilityNodeInfo? {
        if (node == null || depth > 14) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val match = findScrollableNode(node.getChild(i), depth + 1)
            if (match != null) return match
        }
        return null
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo?, depth: Int = 0): AccessibilityNodeInfo? {
        if (node == null || depth > 14) return null
        if (node.isEditable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val match = findFirstEditable(node.getChild(i), depth + 1)
            if (match != null) return match
        }
        return null
    }
}
