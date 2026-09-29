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
     * Presses WhatsApp's Send button. Looks up the button by its view id first, then by its
     * accessibility label. Reads nothing else from the screen. Returns true only if a click ran.
     */
    fun clickSendButton(pkg: String): Boolean {
        val root = rootInActiveWindow ?: return false
        try {
            if (clickFirstClickable(root.findAccessibilityNodeInfosByViewId("$pkg:id/send"))) return true
            for (label in SEND_LABELS) {
                val exact = root.findAccessibilityNodeInfosByText(label)
                    ?.filter { it.contentDescription?.toString()?.trim().equals(label, ignoreCase = true) }
                if (clickFirstClickable(exact)) return true
            }
            return false
        } finally {
            root.recycle()
        }
    }

    /** True while WhatsApp's Send button is still on screen (it turns into the mic once the box is empty). */
    fun hasSendButton(pkg: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            !root.findAccessibilityNodeInfosByViewId("$pkg:id/send").isNullOrEmpty()
        } finally {
            root.recycle()
        }
    }

    private fun clickFirstClickable(nodes: List<AccessibilityNodeInfo>?): Boolean {
        if (nodes.isNullOrEmpty()) return false
        for (node in nodes) {
            val target = if (node.isClickable) node else node.parent?.takeIf { it.isClickable }
            if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return false
    }

    companion object {
        private val SEND_LABELS = listOf("Send", "भेजें", "भेजिए", "ارسال", "بھیجیں", "Enviar")

        var instance: MaxAccessibilityService? = null
            private set
        var currentActivePackage: String? = null
            private set

        fun isEnabled(): Boolean = instance != null
    }
}
