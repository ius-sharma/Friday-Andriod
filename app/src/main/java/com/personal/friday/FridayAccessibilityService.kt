package com.personal.friday

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class FridayAccessibilityService : AccessibilityService() {

    private var lastExternalFocusedNode: AccessibilityNodeInfo? = null
    private var lastExternalPackage: String? = null
    private var lastScrollEventTime = 0L
    private var lastContentChangeCheckTime = 0L
    private var lastReelSignature = ""

    companion object {
        private const val TAG = "FridayAccessibility"

        @Volatile
        var instance: FridayAccessibilityService? = null
            private set

        @Volatile
        var currentForegroundPackage: String? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "FridayAccessibilityService connected and ready for auto-typing!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: return

        // NEVER store or track Friday's own overlay or app as target for typing
        if (pkg == packageName || pkg.startsWith("com.personal.friday")) {
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                currentForegroundPackage = pkg
                Log.d(TAG, "Foreground app switched to: $pkg")
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                currentForegroundPackage = pkg
                val source = event.source
                if (source != null && (source.isEditable || isEditTextClass(source.className))) {
                    lastExternalFocusedNode = source
                    lastExternalPackage = pkg
                    Log.d(TAG, "Stored external focused editable node: class=${source.className}, pkg=$pkg")
                }
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                currentForegroundPackage = pkg
                val source = event.source
                if (source != null && (source.isEditable || isEditTextClass(source.className))) {
                    lastExternalFocusedNode = source
                    lastExternalPackage = pkg
                    Log.d(TAG, "Stored external clicked editable node: class=${source.className}, pkg=$pkg")
                }
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                currentForegroundPackage = pkg
                if (isSupportedReelPackage(pkg)) {
                    val now = System.currentTimeMillis()
                    if (now - lastScrollEventTime >= 1000L) {
                        if (isReelContext(event, pkg)) {
                            lastScrollEventTime = now
                            val sig = extractReelSignature(event, pkg)
                            if (sig.isNotBlank()) lastReelSignature = sig
                            Log.i(TAG, "Confirmed vertical Reel/Short scroll in $pkg -> Counted!")
                            FridayOverlayService.instance?.onReelDetected(pkg)
                        } else {
                            Log.d(TAG, "Standard feed/post scroll in $pkg (not a Reel). Ignored.")
                        }
                    }
                }
            }
            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                currentForegroundPackage = pkg
                if (isSupportedReelPackage(pkg)) {
                    val now = System.currentTimeMillis()
                    if (now - lastScrollEventTime >= 1000L) {
                        if (isReelContext(event, pkg)) {
                            lastScrollEventTime = now
                            val sig = extractReelSignature(event, pkg)
                            if (sig.isNotBlank()) lastReelSignature = sig
                            Log.i(TAG, "Confirmed ViewPager2 page snap in $pkg -> Counted!")
                            FridayOverlayService.instance?.onReelDetected(pkg)
                        }
                    }
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                currentForegroundPackage = pkg
                if (isSupportedReelPackage(pkg)) {
                    val now = System.currentTimeMillis()
                    if (now - lastScrollEventTime >= 1200L && now - lastContentChangeCheckTime >= 800L) {
                        lastContentChangeCheckTime = now
                        if (isReelContext(event, pkg)) {
                            val sig = extractReelSignature(event, pkg)
                            if (sig.isNotBlank() && sig != lastReelSignature) {
                                lastReelSignature = sig
                                lastScrollEventTime = now
                                Log.i(TAG, "Confirmed new Reel signature in $pkg ($sig) -> Counted!")
                                FridayOverlayService.instance?.onReelDetected(pkg)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "FridayAccessibilityService interrupted")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        lastExternalFocusedNode = null
        lastExternalPackage = null
        currentForegroundPackage = null
        instance = null
        Log.i(TAG, "FridayAccessibilityService unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        lastExternalFocusedNode = null
        lastExternalPackage = null
        currentForegroundPackage = null
        instance = null
        super.onDestroy()
        Log.i(TAG, "FridayAccessibilityService destroyed")
    }

    /**
     * Types or pastes text directly into whatever external input field is currently active or focused on screen.
     * Guaranteed to NEVER target Friday's own floating overlays.
     * Returns true if successful, false if no editable field was found.
     */
    fun typeTextIntoActiveField(text: String, append: Boolean = true): Boolean {
        if (text.isBlank()) return false
        Log.i(TAG, "typeTextIntoActiveField requested for text length=${text.length}")

        var targetNode: AccessibilityNodeInfo? = null

        fun isValidTarget(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            val pkg = node.packageName?.toString() ?: ""
            if (pkg == packageName || pkg.startsWith("com.personal.friday")) {
                return false
            }
            return node.isEditable || isEditTextClass(node.className)
        }

        // 1. First priority: Check all interactive windows for an active external app input
        try {
            val windowList = windows
            if (!windowList.isNullOrEmpty()) {
                // Check top application windows first
                for (window in windowList) {
                    val root = window.root ?: continue
                    val rootPkg = root.packageName?.toString() ?: ""
                    if (rootPkg == packageName || rootPkg.startsWith("com.personal.friday")) {
                        continue
                    }

                    // A: check focused input in this window
                    val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    if (isValidTarget(focused)) {
                        targetNode = focused
                        Log.d(TAG, "Found target via window FOCUS_INPUT: pkg=$rootPkg")
                        break
                    }

                    // B: check deep editable in this window
                    val editable = findEditableNode(root)
                    if (isValidTarget(editable)) {
                        targetNode = editable
                        Log.d(TAG, "Found target via window search: pkg=$rootPkg")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error inspecting windows list", e)
        }

        // 2. Second priority: active window root if not Friday
        if (targetNode == null) {
            try {
                val root = rootInActiveWindow
                if (root != null) {
                    val rootPkg = root.packageName?.toString() ?: ""
                    if (rootPkg != packageName && !rootPkg.startsWith("com.personal.friday")) {
                        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                        if (isValidTarget(focused)) {
                            targetNode = focused
                            Log.d(TAG, "Found target via rootInActiveWindow FOCUS_INPUT: pkg=$rootPkg")
                        } else {
                            val editable = findEditableNode(root)
                            if (isValidTarget(editable)) {
                                targetNode = editable
                                Log.d(TAG, "Found target via rootInActiveWindow search: pkg=$rootPkg")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error inspecting rootInActiveWindow", e)
            }
        }

        // 3. Third priority: cached last external focused node
        if (targetNode == null) {
            val cached = lastExternalFocusedNode
            if (cached != null) {
                try {
                    if (cached.refresh() && isValidTarget(cached)) {
                        targetNode = cached
                        Log.d(TAG, "Found target via refreshed lastExternalFocusedNode: pkg=${cached.packageName}")
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Error refreshing cached node", t)
                }
            }
        }

        if (targetNode == null) {
            Log.w(TAG, "No valid external editable field could be located on screen to type into.")
            return false
        }

        // Absolute safeguard: Never type into Friday itself
        val targetPkg = targetNode.packageName?.toString() ?: ""
        if (targetPkg == packageName || targetPkg.startsWith("com.personal.friday")) {
            Log.e(TAG, "Safeguard triggered: Refusing to type into Friday's own overlay node: pkg=$targetPkg")
            return false
        }

        return try {
            // Always copy to clipboard first as a reliable fallback
            copyToClipboard(text)

            // Attempt 1: ACTION_PASTE (best for chat apps like WhatsApp, Telegram, Instagram, Chrome)
            var success = targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            Log.d(TAG, "ACTION_PASTE result: $success in pkg=$targetPkg")

            // Attempt 2: If paste didn't work, use ACTION_SET_TEXT
            if (!success) {
                val existing = targetNode.text?.toString() ?: ""
                val combinedText = if (append && existing.isNotBlank()) "$existing $text" else text
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, combinedText)
                }
                success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                Log.d(TAG, "ACTION_SET_TEXT result: $success in pkg=$targetPkg")

                if (success) {
                    // Try to move cursor to the end
                    try {
                        val selArgs = Bundle().apply {
                            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, combinedText.length)
                            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, combinedText.length)
                        }
                        targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
                    } catch (_: Exception) {}
                }
            }

            // Attempt 3: Final fallback try paste once more
            if (!success) {
                success = targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            }

            success
        } catch (t: Throwable) {
            Log.e(TAG, "Exception while executing typing action", t)
            false
        }
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val pkg = node.packageName?.toString() ?: ""
        if (pkg == packageName || pkg.startsWith("com.personal.friday")) {
            return null
        }
        if (node.isEditable || isEditTextClass(node.className)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditableNode(child)
            if (result != null) return result
        }
        return null
    }

    private fun isEditTextClass(className: CharSequence?): Boolean {
        if (className == null) return false
        val name = className.toString()
        return name.contains("EditText", ignoreCase = true) ||
                name.contains("AutoCompleteTextView", ignoreCase = true) ||
                name.contains("InputConnection", ignoreCase = true)
    }

    fun copyToClipboard(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Friday AutoType", text)
            clipboard.setPrimaryClip(clip)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy to clipboard", e)
        }
    }

    private fun isSupportedReelPackage(pkg: String): Boolean {
        return pkg == "com.instagram.android" ||
               pkg == "com.google.android.youtube" ||
               pkg == "com.facebook.katana" ||
               pkg == "com.facebook.lite" ||
               pkg == "com.zhiliaoapp.musically" ||
               pkg == "com.ss.android.ugc.trill" ||
               pkg == "com.snapchat.android"
    }

    private fun isReelContext(event: AccessibilityEvent, pkg: String): Boolean {
        // 1. Direct event source check (fastest, zero tree search)
        if (isReelEventDirect(event)) {
            return true
        }

        val root = getTargetRoot(pkg) ?: return false

        // 2. High priority: Check for explicit reel/short visual elements on screen
        if (hasVisibleReelSignatures(root, 0)) {
            return true
        }

        // 3. Tab state check
        if (pkg == "com.instagram.android" && isInstagramReelsTabSelected(root)) {
            return true
        }

        if (pkg == "com.google.android.youtube" && isYouTubeShortsActive(root)) {
            return true
        }

        return false
    }

    private fun getTargetRoot(pkg: String): AccessibilityNodeInfo? {
        try {
            val windowList = windows
            if (!windowList.isNullOrEmpty()) {
                for (window in windowList) {
                    val root = window.root ?: continue
                    val rPkg = root.packageName?.toString() ?: ""
                    if (rPkg == pkg) {
                        return root
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error inspecting windows list for reel context", e)
        }
        val active = rootInActiveWindow
        if (active != null) {
            val activePkg = active.packageName?.toString() ?: ""
            if (activePkg != packageName && !activePkg.startsWith("com.personal.friday")) {
                return active
            }
        }
        return null
    }

    private fun isReelEventDirect(event: AccessibilityEvent): Boolean {
        val source = event.source ?: return false
        val resId = source.viewIdResourceName?.lowercase() ?: ""
        val desc = source.contentDescription?.toString()?.lowercase() ?: ""
        val text = source.text?.toString()?.lowercase() ?: ""

        if (text == "reels" || desc == "reels" || text == "shorts" || desc == "shorts" ||
            text.contains("reels tab") || desc.contains("reels tab") ||
            text.contains("shorts tab") || desc.contains("shorts tab")
        ) {
            return true
        }
        if (resId.contains("clips_view_pager") ||
            resId.contains("clips_video_container") ||
            resId.contains("reel_viewer") ||
            resId.contains("shorts_player") ||
            resId.contains("shorts_container") ||
            resId.contains("reel_recycler")
        ) {
            return true
        }
        if (desc.contains("reels") || desc.contains("shorts") || desc.contains("reel by") || desc.contains("like reel")) {
            return true
        }
        if (desc.contains("like this short") || desc.contains("dislike this short") || desc.contains("sound used in this short")) {
            return true
        }
        if (desc.contains("original audio") || text.contains("original audio")) {
            return true
        }
        return false
    }

    private fun hasVisibleReelSignatures(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 14) return false
        if (node.isVisibleToUser) {
            val resId = node.viewIdResourceName?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""

            // 1. Explicit Reel / Shorts keywords in text or contentDescription
            if (text == "reels" || desc == "reels" ||
                text == "shorts" || desc == "shorts" ||
                text.contains("reels tab") || desc.contains("reels tab") ||
                text.contains("shorts tab") || desc.contains("shorts tab")
            ) {
                return true
            }

            // 2. Reel / Short unique actions
            if (desc.contains("like this short") ||
                desc.contains("dislike this short") ||
                desc.contains("sound used in this short") ||
                desc.contains("comments for this short") ||
                desc.contains("share this short") ||
                desc.contains("reel by") ||
                desc.contains("like reel") ||
                desc.contains("remix with") ||
                desc.contains("use template") ||
                desc.contains("watch more reels") ||
                desc.contains("open reels audio") ||
                desc.contains("reels viewer")
            ) {
                return true
            }

            // 3. Audio & Music badges in Reels
            if (text.contains("original audio") || desc.contains("original audio") ||
                text.contains("audio is unavailable") || desc.contains("audio is unavailable")
            ) {
                return true
            }

            // 4. View IDs
            if (resId.contains("clips_video_container") ||
                resId.contains("clips_view_pager") ||
                resId.contains("reel_viewer") ||
                resId.contains("shorts_container") ||
                resId.contains("shorts_player") ||
                resId.contains("reel_player") ||
                resId.contains("reel_recycler")
            ) {
                return true
            }
        }

        val count = node.childCount.coerceAtMost(16)
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            if (hasVisibleReelSignatures(child, depth + 1)) return true
        }
        return false
    }

    private fun isInstagramReelsTabSelected(root: AccessibilityNodeInfo): Boolean {
        fun search(node: AccessibilityNodeInfo?, depth: Int): Boolean {
            if (node == null || depth > 10) return false
            if (node.isVisibleToUser) {
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                if (node.isSelected && desc.contains("reel")) {
                    return true
                }
                if (desc.contains("reels tab, selected") || desc.contains("selected, reels") || desc.contains("reels, tab")) {
                    return true
                }
            }
            val count = node.childCount.coerceAtMost(10)
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                if (search(child, depth + 1)) return true
            }
            return false
        }
        return search(root, 0)
    }

    private fun isYouTubeShortsActive(root: AccessibilityNodeInfo): Boolean {
        fun search(node: AccessibilityNodeInfo?, depth: Int): Boolean {
            if (node == null || depth > 10) return false
            if (node.isVisibleToUser) {
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                val resId = node.viewIdResourceName?.lowercase() ?: ""
                if (node.isSelected && desc.contains("short")) {
                    return true
                }
                if (desc.contains("shorts tab, selected") || desc.contains("selected, shorts") || desc.contains("like this short")) {
                    return true
                }
                if (resId.contains("shorts_player") || resId.contains("reel_player") || resId.contains("shorts_container")) {
                    return true
                }
            }
            val count = node.childCount.coerceAtMost(10)
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                if (search(child, depth + 1)) return true
            }
            return false
        }
        return search(root, 0)
    }

    private fun extractReelSignature(event: AccessibilityEvent, pkg: String): String {
        // Fast path: event text or content description
        val evtDesc = event.contentDescription?.toString()?.trim() ?: ""
        if (evtDesc.contains("reel by", ignoreCase = true) || evtDesc.contains("like this short", ignoreCase = true)) {
            return evtDesc
        }

        // Secondary: Find distinct reel title/author node in target window
        val root = getTargetRoot(pkg) ?: rootInActiveWindow ?: return ""
        fun findSig(node: AccessibilityNodeInfo?, depth: Int): String {
            if (node == null || depth > 9) return ""
            if (node.isVisibleToUser) {
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                if (desc.startsWith("reel by", ignoreCase = true) ||
                    desc.contains("like this short along with", ignoreCase = true) ||
                    desc.contains("sound used in this short", ignoreCase = true)
                ) {
                    return desc
                }
                val text = node.text?.toString()?.trim() ?: ""
                if (text.startsWith("@") && text.length in 3..35) {
                    return text
                }
            }
            val count = node.childCount.coerceAtMost(10)
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                val res = findSig(child, depth + 1)
                if (res.isNotEmpty()) return res
            }
            return ""
        }
        return findSig(root, 0)
    }
}
