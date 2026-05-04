package com.zeny.wazpay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.zeny.wazpay.logic.PreferenceManager
import com.zeny.wazpay.logic.UssdParser
import com.zeny.wazpay.logic.UssdScreen
import com.zeny.wazpay.ui.screens.UssdOverlayContent
import com.zeny.wazpay.ui.theme.WazpayTheme

class UssdService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val submitAction = Runnable { clickSendOrOk() }
    private lateinit var prefs: PreferenceManager
    private lateinit var windowManager: WindowManager
    private var lastHandledSignature: String? = null
    private var lastHandledAtMs: Long = 0L

    // Overlay state
    private var overlayView: ComposeView? = null
    private var overlayOwner: OverlayLifecycleOwner? = null

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onDestroy() {
        removeUssdOverlay()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventPackage = event.packageName?.toString() ?: return
        val active = prefs.transactionInProgress || prefs.balanceCheckInProgress

        if (active) {
            Log.v(TAG, "RawEvent pkg=$eventPackage type=${event.eventType}")
        }

        if (!active) {
            removeUssdOverlay()
            resetProcessingGuards()
            return
        }

        val rootNode = bestUssdRoot(eventPackage) ?: run {
            Log.w(TAG, "No usable root for event from $eventPackage")
            return
        }
        val effectivePkg = rootNode.packageName?.toString() ?: eventPackage

        if (effectivePkg == packageName || effectivePkg == "com.android.systemui") {
            Log.v(TAG, "Skipping own/systemui window ($effectivePkg)")
            return
        }

        // Show overlay the first time we see a non-own, non-systemui window
        if (overlayView == null) showUssdOverlay()

        try {
            val allTexts = mutableListOf<String>()
            findAllTextNodes(rootNode, allTexts)
            if (allTexts.isEmpty()) {
                Log.v(TAG, "No text nodes – effectivePkg=$effectivePkg event=$eventPackage")
                return
            }

            val combinedText = allTexts.joinToString(" | ")

            if (combinedText.length > 400 && (combinedText.contains("Expand") || combinedText.contains("Collapse"))) {
                Log.w(TAG, "Skipping notification-pane content (${combinedText.length} chars) from $effectivePkg")
                return
            }

            Log.d(TAG, "USSD Text [$effectivePkg] (event=$eventPackage): $combinedText")
            Log.d(TAG, "Balance mode=${prefs.balanceCheckInProgress} PayMode=${prefs.transactionInProgress}")

            val screen = UssdParser.parse(allTexts)
            if (shouldSkipDuplicate(screen, allTexts)) return
            Log.d(TAG, "Detected Screen: ${screen::class.simpleName} | text=$combinedText")

            when (screen) {
                is UssdScreen.WelcomeDialog -> {
                    clickSendOrOk()
                }
                is UssdScreen.IfscInput -> {
                    prefs.bankIfsc?.let { ifsc ->
                        findInputNode(rootNode)?.let { autoFillAndSend(it, ifsc) }
                    }
                }
                is UssdScreen.PinInput -> {
                    prefs.pendingPin?.let { pin ->
                        findInputNode(rootNode)?.let { autoFillAndSend(it, pin) }
                    }
                }
                is UssdScreen.SendMoneyMenu -> {
                    if (prefs.balanceCheckInProgress) {
                        val menuText = allTexts.joinToString("\n")
                        val option = UssdParser.findOptionForKeywords(
                            menuText, listOf("Balance", "Check Balance", "Account Balance", "Enquiry")
                        ) ?: "4"
                        findInputNode(rootNode)?.let { autoFillAndSend(it, option) }
                    } else {
                        val recipient = prefs.pendingRecipient
                        val isMobile = recipient?.all { it.isDigit() } == true && recipient.length >= 10
                        UssdParser.findMenuOption(allTexts.joinToString("\n"), isMobile)?.let { option ->
                            findInputNode(rootNode)?.let { autoFillAndSend(it, option) }
                        }
                    }
                }
                is UssdScreen.RecipientInput -> {
                    prefs.pendingRecipient?.let { recipient ->
                        findInputNode(rootNode)?.let { autoFillAndSend(it, recipient) }
                    }
                }
                is UssdScreen.AmountInput -> {
                    prefs.pendingAmount?.let { amount ->
                        findInputNode(rootNode)?.let { autoFillAndSend(it, amount) }
                    }
                }
                is UssdScreen.RemarkInput, is UssdScreen.Confirmation -> {
                    val fullText = allTexts.joinToString("\n")
                    val option = UssdParser.findConfirmationOption(fullText) ?: "1"
                    findInputNode(rootNode)?.let { autoFillAndSend(it, option) }
                }
                is UssdScreen.Success -> {
                    prefs.lastPaymentSuccess = true
                    prefs.lastRefId = screen.refId
                    prefs.pendingRecipient = null
                    prefs.pendingAmount = null
                    prefs.pendingPin = null
                    screen.exitOption?.let { option ->
                        Log.d(TAG, "Success screen contains exit option: $option. Sending it.")
                        findInputNode(rootNode)?.let { autoFillAndSend(it, option) }
                    }
                    bringAppToForeground(delay = 1200)
                }
                is UssdScreen.ExitDialog -> {
                    val exitOption = UssdParser.findExitOption(allTexts.joinToString("\n")) ?: "2"
                    findInputNode(rootNode)?.let { autoFillAndSend(it, exitOption) }
                }
                is UssdScreen.Feedback -> {
                    handleFeedback(rootNode)
                }
                is UssdScreen.BalanceResponse -> {
                    prefs.lastBalance = screen.balance
                    prefs.balanceCheckInProgress = false
                    prefs.pendingPin = null
                    resetProcessingGuards()
                    screen.exitOption?.let { option ->
                        findInputNode(rootNode)?.let { autoFillAndSend(it, option) }
                    } ?: clickSendOrOk()
                    bringAppToForeground(delay = 800)
                }
                is UssdScreen.Error -> {
                    prefs.transactionInProgress = false
                    prefs.balanceCheckInProgress = false
                    prefs.lastError = screen.message
                    resetProcessingGuards()
                    clickSendOrOk()
                    bringAppToForeground(delay = 600)
                }
                UssdScreen.Unknown -> {
                    if (prefs.balanceCheckInProgress) {
                        Log.w(TAG, "Balance check: unrecognised USSD screen. Full text:\n${allTexts.joinToString("\n")}")
                    }
                }
            }

        } catch (e: Exception) { Log.e(TAG, "Service Error", e) }
    }

    private fun handleFeedback(rootNode: AccessibilityNodeInfo) {
        Log.i(TAG, "Dismissing final dialog")
        val cancelButton = findClickableWithKeywords(rootNode, listOf("cancel", "exit", "close", "dismiss"))
        if (cancelButton != null) {
            cancelButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            clickSendOrOk()
        }
        prefs.transactionInProgress = false
        resetProcessingGuards()
        bringAppToForeground(delay = 600)
    }

    // ── Overlay ──────────────────────────────────────────────────────────────

    private inner class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle = registry
        override val savedStateRegistry: SavedStateRegistry = controller.savedStateRegistry

        fun start() {
            controller.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun stop() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    private fun showUssdOverlay() {
        if (overlayView != null) return
        val isBalanceCheck = prefs.balanceCheckInProgress
        val owner = OverlayLifecycleOwner()
        owner.start()
        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                WazpayTheme {
                    UssdOverlayContent(isBalanceCheck)
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE
        )
        overlayOwner = owner
        overlayView = view
        try {
            windowManager.addView(view, params)
            Log.d(TAG, "USSD overlay shown (balance=$isBalanceCheck)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
            owner.stop()
            overlayView = null
            overlayOwner = null
        }
    }

    private fun removeUssdOverlay() {
        val view = overlayView ?: return
        try { windowManager.removeView(view) } catch (_: Exception) {}
        overlayOwner?.stop()
        overlayView = null
        overlayOwner = null
        Log.d(TAG, "USSD overlay removed")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun bestUssdRoot(eventPackage: String): AccessibilityNodeInfo? {
        val ownPkg = packageName
        try {
            val allWindows = windows
            if (!allWindows.isNullOrEmpty()) {
                allWindows.forEachIndexed { i, win ->
                    Log.v(TAG, "  win[$i] pkg=${win.root?.packageName} type=${win.type}")
                }
                val telecomWindow = allWindows.firstOrNull { win ->
                    val pkg = win.root?.packageName?.toString() ?: ""
                    pkg.isNotEmpty() && pkg != ownPkg && pkg != "com.android.systemui" && isKnownTelecomPkg(pkg)
                }
                if (telecomWindow != null) {
                    Log.v(TAG, "bestUssdRoot: telecom window pkg=${telecomWindow.root?.packageName}")
                    return telecomWindow.root
                }
                val otherWindow = allWindows.firstOrNull { win ->
                    val pkg = win.root?.packageName?.toString() ?: ""
                    pkg.isNotEmpty() && pkg != ownPkg && pkg != "com.android.systemui"
                }
                if (otherWindow != null) {
                    Log.v(TAG, "bestUssdRoot: other window pkg=${otherWindow.root?.packageName}")
                    return otherWindow.root
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "bestUssdRoot: error iterating windows", e)
        }
        val root = rootInActiveWindow
        val rootPkg = root?.packageName?.toString() ?: ""
        if (rootPkg == ownPkg || rootPkg == "com.android.systemui") {
            Log.w(TAG, "bestUssdRoot: active window is own/systemui ($rootPkg) — skipping")
            return null
        }
        Log.v(TAG, "bestUssdRoot: fallback rootInActiveWindow pkg=$rootPkg")
        return root
    }

    private fun isKnownTelecomPkg(pkg: String): Boolean {
        if (pkg in telecomPackages) return true
        val normalized = pkg.lowercase()
        return telecomPackageHints.any { normalized.contains(it) }
    }

    private fun findAllTextNodes(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        node.text?.let { texts.add(it.toString()) }
        node.contentDescription?.let { texts.add(it.toString()) }
        for (i in 0 until node.childCount) node.getChild(i)?.let { findAllTextNodes(it, texts) }
    }

    private fun autoFillAndSend(node: AccessibilityNodeInfo, text: String) {
        Log.i(TAG, "Auto-filling text: '$text'")
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.i(TAG, "Set text result: $success")
        if (!success) {
            val clipboard = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE, clipboard)
        }
        handler.removeCallbacks(submitAction)
        handler.postDelayed(submitAction, 800)
    }

    private fun shouldSkipDuplicate(screen: UssdScreen, texts: List<String>): Boolean {
        val signature = "${screen::class.java.name}|${texts.joinToString("\n")}"
        val now = SystemClock.uptimeMillis()
        if (signature == lastHandledSignature && now - lastHandledAtMs < DUPLICATE_WINDOW_COOLDOWN_MS) {
            Log.d(TAG, "Skipping duplicate USSD window for ${screen::class.simpleName}")
            return true
        }
        lastHandledSignature = signature
        lastHandledAtMs = now
        return false
    }

    private fun resetProcessingGuards() {
        handler.removeCallbacks(submitAction)
        lastHandledSignature = null
        lastHandledAtMs = 0L
    }

    private fun findInputNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable ||
            node.className?.contains("EditText", true) == true ||
            (node.actions and AccessibilityNodeInfo.ACTION_SET_TEXT != 0)) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { findInputNode(it)?.let { found -> return found } }
        }
        return null
    }

    private fun clickSendOrOk() {
        val root = rootInActiveWindow ?: return
        val positiveKeywords = listOf("send", "ok", "submit", "accept", "reply", "answer", "done", "confirm", "call", "dial", "proceed")
        val positiveButton = findClickableWithKeywords(root, positiveKeywords)
        if (positiveButton != null) {
            Log.d(TAG, "Clicking positive button found by keyword: ${positiveButton.text}")
            positiveButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        val allClickables = mutableListOf<AccessibilityNodeInfo>()
        findAllClickableNodes(root, allClickables)
        if (allClickables.isNotEmpty()) {
            val filtered = allClickables.filter { node ->
                val text = (node.text ?: node.contentDescription ?: "").toString().lowercase()
                !text.contains("cancel") && !text.contains("exit") && !text.contains("close") && !text.contains("dismiss")
            }
            if (filtered.isNotEmpty()) {
                val target = filtered.last()
                Log.d(TAG, "Clicking best-guess clickable node: ${target.text ?: target.className ?: "unlabeled"}")
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }
        if (allClickables.size == 1) {
            Log.d(TAG, "Clicking the only available clickable node")
            allClickables[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        Log.e(TAG, "Could not find a button to click!")
    }

    private fun findAllClickableNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        if (node.isClickable && !node.isEditable) {
            list.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { findAllClickableNodes(it, list) }
        }
    }

    private fun findClickableWithKeywords(node: AccessibilityNodeInfo, keywords: List<String>): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (keywords.any { text.contains(it) || contentDesc.contains(it) }) {
            var current: AccessibilityNodeInfo? = node
            while (current != null) {
                if (current.isClickable) return AccessibilityNodeInfo.obtain(current)
                current = current.parent
            }
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { findClickableWithKeywords(it, keywords)?.let { found -> return found } }
        }
        return null
    }

    private fun bringAppToForeground(delay: Long) {
        handler.postDelayed({
            removeUssdOverlay()
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(intent)
        }, delay)
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "WazPay-USSD"
        private val telecomPackages = setOf("com.android.phone", "com.android.server.telecom", "com.google.android.dialer", "com.samsung.android.incallui", "com.android.systemui")
        private val telecomPackageHints = setOf("dialer", "telecom", "phone", "telephony", "incall", "callui", "systemui")
        private const val DUPLICATE_WINDOW_COOLDOWN_MS = 1200L
    }
}
