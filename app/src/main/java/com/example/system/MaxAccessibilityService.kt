package com.example.system

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MaxAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName != null) {
            currentActivePackage = event.packageName.toString()
        }
    }

    override fun onInterrupt() {
        // Interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    fun getScreenTextSummary(): String {
        val rootNode = rootInActiveWindow ?: return "Screen content unavailable or locked."
        val textList = mutableListOf<String>()
        collectNodeText(rootNode, textList)
        rootNode.recycle()
        return if (textList.isNotEmpty()) {
            "Current App: ${currentActivePackage ?: "Active Window"}\nVisible Elements:\n" + textList.take(20).joinToString("\n• ")
        } else {
            "No text content detected on screen."
        }
    }

    private fun collectNodeText(node: AccessibilityNodeInfo, textList: MutableList<String>) {
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()

        if (!text.isNullOrEmpty() && text.length > 1) {
            val prefix = if (node.isClickable) "[Button] " else ""
            textList.add("$prefix$text")
        } else if (!desc.isNullOrEmpty() && desc.length > 1) {
            val prefix = if (node.isClickable) "[Button] " else ""
            textList.add("$prefix$desc")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNodeText(child, textList)
            child.recycle()
        }
    }

    fun clickElementByText(queryText: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(queryText)
        var clicked = false
        if (!nodes.isNullOrEmpty()) {
            for (node in nodes) {
                if (node.isClickable) {
                    clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) break
                } else if (node.parent != null && node.parent.isClickable) {
                    clicked = node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) break
                }
            }
        }
        rootNode.recycle()
        return clicked
    }

    /**
     * Finds the main on/off Switch on the current screen (e.g. the big toggle at the top of
     * Settings > Wi-Fi or Settings > Bluetooth) and taps it only if it isn't already in the
     * requested state. This is how MAX actually turns things ON/OFF instead of just opening
     * the settings page and leaving the tap to the user — there's no public Android API for a
     * regular app to flip these switches directly (that's been locked down since Android 6 to
     * stop apps silently changing radios), so driving the real on-screen toggle via the
     * Accessibility Service is the only non-root way to do it.
     *
     * Returns true if the switch ended up in [desiredOn] state (either it already was, or the
     * tap succeeded), false if no switch was found or the tap failed.
     */
    fun setMainSwitchState(desiredOn: Boolean?): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val switchNode = findFirstSwitch(rootNode)
        val result = if (switchNode == null) {
            false
        } else if (desiredOn == null) {
            // No explicit on/off requested — just toggle whatever it currently is.
            switchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else if (switchNode.isChecked == desiredOn) {
            true // already in the state we want, nothing to do
        } else {
            switchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        switchNode?.recycle()
        rootNode.recycle()
        return result
    }

    private fun findFirstSwitch(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className == "android.widget.Switch" || node.className == "android.widget.ToggleButton") {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstSwitch(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    /**
     * Heuristic tap on the first real video result row in a freshly opened YouTube search
     * results screen, so "search and play X on YouTube" actually starts playback instead of
     * just leaving the results list open. There's no official "play the first search result"
     * intent, and using the YouTube Data API for this would need a Google Cloud API key —
     * this avoids that entirely by just tapping the screen the same way a finger would.
     *
     * Heuristic: skip the search bar / top app bar area (roughly the top ~280px, which is
     * where "back", the search box, and the account icon usually sit), then click the first
     * clickable node found below that which is tall enough to plausibly be a video thumbnail
     * row (>= 150px tall) rather than a small icon/button. Not guaranteed on every device/UI
     * version of the YouTube app, but works on the standard layout.
     */
    fun clickFirstVideoResult(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectClickableCandidates(rootNode, candidates)
        val bounds = android.graphics.Rect()
        val target = candidates.firstOrNull { node ->
            node.getBoundsInScreen(bounds)
            bounds.top > 280 && bounds.height() >= 150
        }
        val clicked = target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        candidates.forEach { it.recycle() }
        rootNode.recycle()
        return clicked
    }

    private fun collectClickableCandidates(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        if (node.isClickable) {
            out.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectClickableCandidates(child, out)
            child.recycle()
        }
    }

    companion object {
        var instance: MaxAccessibilityService? = null
            private set
        var currentActivePackage: String? = null
            private set

        fun isEnabled(): Boolean = instance != null
    }
}
