package com.zeny.wazpay

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zeny.wazpay.logic.PreferenceManager
import com.zeny.wazpay.ui.screens.*
import com.zeny.wazpay.ui.theme.WazpayTheme

private const val TAG = "WazPay-Main"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferenceManager = PreferenceManager(this)
        
        // Only clear state if not already in progress to avoid resetting during rotation or brief background
        if (!preferenceManager.transactionInProgress) {
            Log.i(TAG, "App started - Clearing state")
            preferenceManager.clearTransactionState()
        }
        
        enableEdgeToEdge()
        setContent {
            WazpayTheme {
                MainContent(preferenceManager)
            }
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC)
    for (enabledService in enabledServices) {
        if (enabledService.resolveInfo.serviceInfo.packageName == context.packageName &&
            enabledService.resolveInfo.serviceInfo.name == service.name) {
            return true
        }
    }
    return false
}

@Composable
fun MainContent(prefs: PreferenceManager) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val requiredPermissions = arrayOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.CAMERA
    )
    
    var isAccessEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context, UssdService::class.java)) }
    var hasPermissions by remember {
        mutableStateOf(requiredPermissions.all { 
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED 
        })
    }
    
    var screenState by rememberSaveable { mutableStateOf("SPLASH") } 
    
    var recipient by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var upiPin by rememberSaveable { mutableStateOf("") }
    var recentRecipients by remember { mutableStateOf(prefs.getRecentRecipients().toList()) }

    var isScannedInput by remember { mutableStateOf(false) }

    LaunchedEffect(screenState) {
        if (screenState == "RECIPIENT" && !isScannedInput) {
            amount = ""
            upiPin = ""
        }
        if (screenState != "SCANNER" && screenState != "AMOUNT") {
            isScannedInput = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessEnabled = isAccessibilityServiceEnabled(context, UssdService::class.java)
                hasPermissions = requiredPermissions.all { 
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED 
                }
                
                if (prefs.lastPaymentSuccess) {
                    screenState = "SUCCESS"
                }
                if (screenState == "BALANCE_CHECKING") {
                    when {
                        prefs.lastBalance != null -> screenState = "BALANCE_RESULT"
                        !prefs.balanceCheckInProgress -> {
                            // Don't clear yet if there's an error — let the screen show it
                            if (prefs.lastError == null) {
                                prefs.clearBalanceState()
                                screenState = "RECIPIENT"
                            }
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = screenState != "RECIPIENT" && screenState != "SETUP" && screenState != "SPLASH") {
        when (screenState) {
            "SCANNER" -> screenState = "RECIPIENT"
            "AMOUNT" -> screenState = "RECIPIENT"
            "PIN" -> screenState = "AMOUNT"
            "SUCCESS" -> screenState = "RECIPIENT"
            "PROCESSING" -> screenState = "RECIPIENT"
            "PROFILE" -> screenState = "RECIPIENT"
            "BALANCE_CHECK" -> screenState = "RECIPIENT"
            "BALANCE_CHECKING" -> {
                prefs.clearBalanceState()
                screenState = "RECIPIENT"
            }
            "BALANCE_RESULT" -> {
                prefs.clearBalanceState()
                screenState = "RECIPIENT"
            }
        }
    }

    val currentView = when {
        screenState == "SPLASH" -> "SPLASH"
        !hasPermissions -> "PERMISSION"
        !isAccessEnabled && screenState != "SETUP" -> "ACCESSIBILITY"
        else -> screenState
    }

    Surface(
        modifier = Modifier.fillMaxSize(), 
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState = currentView,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "ScreenTransition",
            contentAlignment = Alignment.Center
        ) { state ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when (state) {
                    "SPLASH" -> SplashScreen {
                        screenState = if (prefs.bankIfsc.isNullOrEmpty()) "SETUP" else "RECIPIENT"
                    }
                    "PERMISSION" -> OnboardingScreen(
                        title = "Permissions Required",
                        description = "WazPay needs Phone, Call, and Camera permissions to process payments and scan QR codes securely.",
                        icon = Icons.Default.Security,
                        actionLabel = "Grant Permissions",
                        onAction = { permissionLauncher.launch(requiredPermissions) }
                    )
                    "ACCESSIBILITY" -> OnboardingScreen(
                        title = "Enable Accessibility",
                        description = "WazPay needs its Accessibility Service to automate USSD menus. Tap below to open the toggle — it's one switch.",
                        icon = Icons.Default.Accessibility,
                        actionLabel = "Enable Now",
                        onAction = { openAccessibilityServiceSettings(context, UssdService::class.java) }
                    )
                    "SETUP" -> SetupScreen(onComplete = { ifsc, sim ->
                        prefs.bankIfsc = ifsc
                        prefs.selectedSim = sim
                        screenState = "RECIPIENT"
                    })
                    "RECIPIENT" -> RecipientScreen(
                        value = recipient,
                        onValueChange = { recipient = it },
                        onNext = {
                            prefs.addRecentRecipient(recipient)
                            recentRecipients = prefs.getRecentRecipients().toList()
                            screenState = "AMOUNT"
                        },
                        onScanClick = { screenState = "SCANNER" },
                        recentRecipients = recentRecipients,
                        onProfileClick = { screenState = "PROFILE" },
                        onCheckBalanceClick = { screenState = "BALANCE_CHECK" }
                    )
                    "SCANNER" -> QrScannerScreen(
                        onScanned = { upiId ->
                            recipient = upiId
                            isScannedInput = true
                            screenState = "AMOUNT"
                        },
                        onBack = { screenState = "RECIPIENT" }
                    )
                    "AMOUNT" -> AmountScreen(amount, onValueChange = { amount = it }, onNext = { screenState = "PIN" }, onBack = { screenState = "RECIPIENT" })
                    "PIN" -> PinScreen(upiPin, onValueChange = { upiPin = it }, onPay = {
                        prefs.pendingRecipient = recipient
                        prefs.pendingAmount = amount
                        prefs.pendingPin = upiPin
                        prefs.transactionInProgress = true
                        prefs.lastPaymentSuccess = false
                        prefs.lastError = null
                        
                        screenState = "PROCESSING"
                        initiatePayment(context, recipient, amount, prefs.selectedSim)
                    }, onBack = { screenState = "AMOUNT" })
                    "PROCESSING" -> TransactionProcessingScreen(
                        error = prefs.lastError,
                        onCancel = { 
                            prefs.transactionInProgress = false
                            prefs.lastError = null
                            screenState = "RECIPIENT" 
                        }
                    )
                    "SUCCESS" -> {
                        val name = prefs.lastRecipientName ?: recipient
                        val refId = prefs.lastRefId ?: "N/A"
                        val successAmount = prefs.pendingAmount ?: amount
                        LaunchedEffect(Unit) {
                            prefs.addTransactionRecord(
                                recipient = prefs.pendingRecipient ?: recipient,
                                amount = successAmount,
                                refId = refId
                            )
                        }
                        SuccessScreen(name, refId, successAmount) {
                            prefs.clearTransactionState()
                            recipient = ""; amount = ""; upiPin = ""; screenState = "RECIPIENT"
                        }
                    }
                    "PROFILE" -> ProfileScreen(
                        prefs = prefs,
                        onBack = { screenState = "RECIPIENT" },
                        onEditSettings = { screenState = "SETUP" }
                    )
                    "BALANCE_CHECK" -> PinScreen(
                        value = upiPin,
                        onValueChange = { upiPin = it },
                        actionLabel = "Check Balance",
                        onBack = { screenState = "RECIPIENT" },
                        onPay = {
                            prefs.lastError = null
                            prefs.pendingPin = upiPin
                            prefs.balanceCheckInProgress = true
                            upiPin = ""
                            screenState = "BALANCE_CHECKING"
                            initiateBalanceCheck(context, prefs.selectedSim)
                        }
                    )
                    "BALANCE_CHECKING" -> {
                        var balanceError by remember { mutableStateOf<String?>(null) }
                        LaunchedEffect(Unit) {
                            val deadline = System.currentTimeMillis() + 90_000L
                            while (System.currentTimeMillis() < deadline) {
                                kotlinx.coroutines.delay(1_000)
                                if (prefs.lastBalance != null) { screenState = "BALANCE_RESULT"; break }
                                if (!prefs.balanceCheckInProgress) {
                                    val err = prefs.lastError
                                    if (err != null) {
                                        balanceError = err
                                    } else {
                                        prefs.clearBalanceState()
                                        screenState = "RECIPIENT"
                                    }
                                    break
                                }
                            }
                            if (screenState == "BALANCE_CHECKING" && balanceError == null) {
                                prefs.clearBalanceState()
                                screenState = "RECIPIENT"
                            }
                        }
                        BalanceCheckingScreen(
                            error = balanceError,
                            onCancel = {
                                prefs.clearBalanceState()
                                screenState = "RECIPIENT"
                            }
                        )
                    }
                    "BALANCE_RESULT" -> {
                        val balance = prefs.lastBalance ?: "Unavailable"
                        BalanceResultScreen(balance = balance) {
                            prefs.clearBalanceState()
                            screenState = "RECIPIENT"
                        }
                    }
                }
            }
        }
    }
}

private fun openAccessibilityServiceSettings(context: Context, serviceClass: Class<*>) {
    if (Build.VERSION.SDK_INT >= 33) {
        try {
            val intent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            intent.putExtra("android.intent.extra.COMPONENT_NAME",
                ComponentName(context, serviceClass).flattenToString())
            context.startActivity(intent)
            return
        } catch (_: Exception) {}
    }
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

private fun initiateBalanceCheck(context: Context, simIndex: Int) {
    val ussdCode = "*99#"
    Log.d(TAG, "Dialing balance check USSD: $ussdCode on SIM $simIndex")
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
        Log.w(TAG, "CALL_PHONE permission missing during initiateBalanceCheck")
        return
    }
    val uri = Uri.parse("tel:${Uri.encode(ussdCode)}")
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    val extras = Bundle()
    try {
        val phoneAccounts = telecomManager.callCapablePhoneAccounts
        if (phoneAccounts != null && simIndex < phoneAccounts.size) {
            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccounts[simIndex])
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error setting phone account handle for balance check", e)
    }
    telecomManager.placeCall(uri, extras)
}

private fun initiatePayment(context: Context, recipient: String, amount: String, simIndex: Int) {
    val ussdCode = "*99*1#"
    Log.d(TAG, "Dialing USSD: $ussdCode on SIM $simIndex")
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
        Log.w(TAG, "CALL_PHONE permission missing during initiatePayment")
        return
    }
    val uri = Uri.parse("tel:${Uri.encode(ussdCode)}")
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    val extras = Bundle()
    try {
        val phoneAccounts = telecomManager.callCapablePhoneAccounts
        if (phoneAccounts != null && simIndex < phoneAccounts.size) {
            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccounts[simIndex])
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error setting phone account handle", e)
    }
    telecomManager.placeCall(uri, extras)
}
