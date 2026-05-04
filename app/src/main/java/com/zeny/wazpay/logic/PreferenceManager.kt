package com.zeny.wazpay.logic

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wazpay_prefs", Context.MODE_PRIVATE)

    var bankIfsc: String?
        get() = prefs.getString("bank_ifsc", "")
        set(value) = prefs.edit { putString("bank_ifsc", value) }

    var selectedSim: Int
        get() = prefs.getInt("selected_sim", 0)
        set(value) = prefs.edit { putInt("selected_sim", value) }

    var transactionInProgress: Boolean
        get() = prefs.getBoolean("transaction_in_progress", false)
        set(value) = prefs.edit { putBoolean("transaction_in_progress", value) }

    var lastPaymentSuccess: Boolean
        get() = prefs.getBoolean("last_payment_success", false)
        set(value) = prefs.edit { putBoolean("last_payment_success", value) }

    var lastError: String?
        get() = prefs.getString("last_error", null)
        set(value) = prefs.edit { putString("last_error", value) }

    var pendingRecipient: String?
        get() = prefs.getString("pending_recipient", null)
        set(value) = prefs.edit { putString("pending_recipient", value) }

    var pendingAmount: String?
        get() = prefs.getString("pending_amount", null)
        set(value) = prefs.edit { putString("pending_amount", value) }

    var pendingPin: String?
        get() = prefs.getString("pending_pin", null)
        set(value) = prefs.edit { putString("pending_pin", value) }

    var lastRecipientName: String?
        get() = prefs.getString("last_recipient_name", null)
        set(value) = prefs.edit { putString("last_recipient_name", value) }

    var lastRefId: String?
        get() = prefs.getString("last_ref_id", null)
        set(value) = prefs.edit { putString("last_ref_id", value) }

    // Balance check
    var balanceCheckInProgress: Boolean
        get() = prefs.getBoolean("balance_check_in_progress", false)
        set(value) = prefs.edit { putBoolean("balance_check_in_progress", value) }

    var lastBalance: String?
        get() = prefs.getString("last_balance", null)
        set(value) = prefs.edit { putString("last_balance", value) }

    // Transaction history – stored as "timestamp|recipient|amount|refId"
    data class TransactionRecord(
        val recipient: String,
        val amount: String,
        val refId: String,
        val timestamp: Long
    )

    fun getTransactionHistory(): List<TransactionRecord> {
        val set = prefs.getStringSet("tx_history", emptySet()) ?: emptySet()
        return set.mapNotNull {
            val p = it.split("|")
            if (p.size >= 4) TransactionRecord(p[1], p[2], p[3], p[0].toLongOrNull() ?: 0L)
            else null
        }.sortedByDescending { it.timestamp }
    }

    fun addTransactionRecord(recipient: String, amount: String, refId: String) {
        val current = prefs.getStringSet("tx_history", emptySet())?.toMutableSet() ?: mutableSetOf()
        val entry = "${System.currentTimeMillis()}|$recipient|$amount|$refId"
        val updated = (current + setOf(entry))
            .sortedByDescending { it.substringBefore("|").toLongOrNull() ?: 0L }
            .take(20).toSet()
        prefs.edit { putStringSet("tx_history", updated) }
    }

    fun getRecentRecipients(): Set<String> = prefs.getStringSet("recent_recipients", emptySet()) ?: emptySet()

    fun addRecentRecipient(recipient: String) {
        val current = getRecentRecipients().toMutableSet()
        val updated = (setOf(recipient) + current).take(5).toSet()
        prefs.edit { putStringSet("recent_recipients", updated) }
    }

    fun clearTransactionState() {
        prefs.edit {
            putBoolean("transaction_in_progress", false)
            remove("last_payment_success")
            remove("last_error")
            remove("pending_recipient")
            remove("pending_amount")
            remove("pending_pin")
            remove("last_recipient_name")
            remove("last_ref_id")
        }
    }

    fun clearBalanceState() {
        prefs.edit {
            putBoolean("balance_check_in_progress", false)
            remove("pending_pin")
            remove("last_balance")
            remove("last_error")
        }
    }
}
