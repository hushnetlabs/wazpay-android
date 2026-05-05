package com.zeny.wazpay.logic

import android.util.Log

object UssdParser {
    private const val TAG = "UssdParser"

    // Pre-compiled Regexes to save CPU cycles
    private val SEND_MONEY_REGEX = Regex(
        "\\d\\.?\\s*(Mobile|UPI|VPA|Balance|Request|Receive|Profile|Pending|Enquiry|Inquiry|Transaction)",
        RegexOption.IGNORE_CASE
    )
    private val DECIMAL_REGEX = Regex("[0-9]+\\.[0-9]{2}")
    private val BALANCE_REGEX_1 = Regex("(?:Rs\\.?|₹|INR)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)
    private val BALANCE_REGEX_2 = Regex("([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:Rs\\.?|₹|INR)", RegexOption.IGNORE_CASE)
    private val REF_ID_REGEX = Regex("(?:RefId|Ref|Txn|Reference|ID|Id is)[:\\s]*([A-Z\\d]{8,})", RegexOption.IGNORE_CASE)
    private val EXIT_FALLBACK_REGEX = Regex("(\\d+)\\s*[:.)\\s-]*\\s*(?:exit|back|cancel|quit)", RegexOption.IGNORE_CASE)
    private val TO_EXIT_REGEX = Regex("(\\d+)\\s*to\\s*exit", RegexOption.IGNORE_CASE)

    // Option Matching Regexes
    private val EXIT_REGEXES = buildOptionRegexes(listOf("Exit", "Quit", "Close", "Back", "Cancel"))
    private val CONFIRM_REGEXES = buildOptionRegexes(listOf("Confirm", "Proceed", "Yes", "Pay", "Ok"))
    private val MOBILE_REGEXES = buildOptionRegexes(listOf("Mobile Number", "Mobile", "Beneficiary", "Phone"))
    private val UPI_REGEXES = buildOptionRegexes(listOf("UPI ID", "VPA", "Virtual ID", "UPI"))
    val BALANCE_MENU_REGEXES = buildOptionRegexes(listOf("Balance", "Check Balance", "Account Balance", "Enquiry"))

    private fun buildOptionRegexes(keywords: List<String>): List<Regex> {
        val escaped = keywords.joinToString("|") { Regex.escape(it) }
        return listOf(
            Regex("(\\d+)\\s*[:.)\\s-]*\\s*(?:$escaped)", RegexOption.IGNORE_CASE),
            Regex("(?:$escaped)\\s*[:.)\\s-]*\\s*(\\d+)", RegexOption.IGNORE_CASE),
            Regex("(\\d+)\\s*to\\s*(?:$escaped)", RegexOption.IGNORE_CASE)
        )
    }

    private fun findOptionFromRegexes(text: String, regexes: List<Regex>): String? {
        for (regex in regexes) {
            regex.find(text)?.let { return it.groupValues[1] }
        }
        return null
    }

    fun parse(texts: List<String>): UssdScreen {
        val fullText = texts.joinToString("\n")
        
        return when {
            isPinPrompt(fullText) -> UssdScreen.PinInput
            isIfscPrompt(fullText) -> UssdScreen.IfscInput
            isWelcomeDialog(fullText) -> UssdScreen.WelcomeDialog
            isSendMoneyMenu(fullText) -> UssdScreen.SendMoneyMenu
            isErrorMessage(fullText) -> UssdScreen.Error(fullText, findExitOption(fullText))
            isRecipientPrompt(fullText) -> UssdScreen.RecipientInput
            isAmountPrompt(fullText) -> UssdScreen.AmountInput
            isRemarkPrompt(fullText) -> UssdScreen.RemarkInput
            isConfirmationPrompt(fullText) -> UssdScreen.Confirmation
            isBalanceResponse(fullText) -> UssdScreen.BalanceResponse(extractBalance(fullText), findExitOption(fullText))
            isSuccessMessage(fullText) -> UssdScreen.Success(extractRefId(fullText), findExitOption(fullText))
            isFeedbackPrompt(fullText) -> UssdScreen.Feedback
            isExitDialog(fullText) -> UssdScreen.ExitDialog
            else -> UssdScreen.Unknown
        }
    }

    private fun isPinPrompt(text: String): Boolean =
        (text.contains("PIN", true) || text.contains("MPIN", true)) && text.contains("Enter", true)

    private fun isIfscPrompt(text: String): Boolean =
        text.contains("IFSC", true) && (text.contains("Enter", true) || text.contains("First 4", true))

    private fun isWelcomeDialog(text: String): Boolean =
        text.contains("welcome", true) && (text.contains("star 99", true) || text.contains("*99#", true))

    private fun isSendMoneyMenu(text: String): Boolean =
        (text.contains("Send Money", true) || text.contains("Transfer", true)) &&
                SEND_MONEY_REGEX.containsMatchIn(text)

    private fun isRecipientPrompt(text: String): Boolean =
        (text.contains("Enter", true) || text.contains("Mobile", true) || text.contains("UPI", true) || text.contains("Beneficiary", true)) &&
                !text.contains("PIN", true) && !text.contains("Amount", true) &&
                !text.contains("Remark", true) && 
                !text.contains("Success", true) &&
                !isSendMoneyMenu(text)

    private fun isAmountPrompt(text: String): Boolean = text.contains("Enter Amount", true)

    private fun isRemarkPrompt(text: String): Boolean = text.contains("Remark", true)

    private fun isConfirmationPrompt(text: String): Boolean = 
        text.contains("Confirm", true) || text.contains("Proceed", true)

    private fun isSuccessMessage(text: String): Boolean =
        (text.contains("success", true) || text.contains("completed", true) ||
                text.contains("sent to", true) || text.contains("paid to", true)) && !text.contains("1.confirm", true)

    fun isExitDialog(text: String): Boolean = 
        findExitOption(text) != null || text.contains("to exit", true) || 
                text.contains("2. exit", true) || text.contains("0. exit", true)

    fun findExitOption(text: String): String? {
        val option = findOptionFromRegexes(text, EXIT_REGEXES)
        if (option != null) return option
        EXIT_FALLBACK_REGEX.find(text)?.let { return it.groupValues[1] }
        TO_EXIT_REGEX.find(text)?.let { return it.groupValues[1] }
        if (text.contains("Exit", true)) return "2"
        return null
    }

    fun findConfirmationOption(text: String): String? {
        return findOptionFromRegexes(text, CONFIRM_REGEXES)
    }

    fun findMenuOption(text: String, isMobile: Boolean): String? {
        return findOptionFromRegexes(text, if (isMobile) MOBILE_REGEXES else UPI_REGEXES)
    }

    fun findBalanceOption(text: String): String? {
        return findOptionFromRegexes(text, BALANCE_MENU_REGEXES)
    }

    private fun isFeedbackPrompt(text: String): Boolean = 
        text.contains("thank you", true) || 
                text.contains("services", true) || 
                text.contains("feedback", true) ||
                text.contains("rate us", true)

    private fun isErrorMessage(text: String): Boolean = 
        text.contains("failed", true) || text.contains("invalid", true) || text.contains("error", true) || text.contains("unable", true)

    private fun isBalanceResponse(text: String): Boolean {
        val hasBalanceWord = text.contains("balance", true) || text.contains("available", true)
        val hasCurrencyOrAmount = text.contains("rs", true) || text.contains("₹") ||
                text.contains("inr", true) || DECIMAL_REGEX.containsMatchIn(text)
        return hasBalanceWord && hasCurrencyOrAmount && !isSendMoneyMenu(text) && !isAmountPrompt(text)
    }

    fun extractBalance(text: String): String {
        BALANCE_REGEX_1.find(text)?.groupValues?.get(1)?.let { return "₹${it.replace(",", "")}" }
        BALANCE_REGEX_2.find(text)?.groupValues?.get(1)?.let { return "₹${it.replace(",", "")}" }
        return text.lines().firstOrNull { it.contains("balance", true) }?.trim() ?: text.take(100)
    }

    private fun extractRefId(text: String): String? {
        return REF_ID_REGEX.find(text)?.groupValues?.get(1)
    }
}
