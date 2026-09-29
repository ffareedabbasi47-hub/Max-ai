package com.example.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.core.FuzzyMatch
import com.example.core.MaxContact
import com.example.core.MaxContactResolver
import com.example.system.MaxAccessibilityService
import kotlinx.coroutines.delay

sealed class ContactLookup {
    /** [confident] = false means the name only roughly matched, so MAX must ask before acting. */
    data class Found(val contact: MaxContact, val confident: Boolean) : ContactLookup()
    data class NotFound(val spoken: String) : ContactLookup()
    data class NoAccess(val spoken: String) : ContactLookup()
}

/**
 * Real phone-call and WhatsApp actions. Every function returns a [ToolResult] that says what
 * ACTUALLY happened — MAX only claims success when the step really completed.
 */
object ContactActions {

    private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    // ---- Contact lookup ---------------------------------------------------------------------

    fun lookup(context: Context, spokenName: String): ContactLookup {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ContactLookup.NoAccess("Contacts ki permission chahiye. Settings me MAX ko Contacts allow kar do.")
        }
        return when (val outcome = MaxContactResolver.match(context, spokenName)) {
            is FuzzyMatch.MatchOutcome.Confident -> ContactLookup.Found(outcome.item, true)
            is FuzzyMatch.MatchOutcome.NeedsConfirmation -> ContactLookup.Found(outcome.item, false)
            is FuzzyMatch.MatchOutcome.Rejected ->
                ContactLookup.NotFound("'$spokenName' naam ka koi contact nahi mila.")
        }
    }

    // ---- Phone call -------------------------------------------------------------------------

    /**
     * Places the call directly when CALL_PHONE is granted; otherwise opens the dialer pre-filled
     * and says so. "Success" means the call was started — not that the other person answered.
     */
    fun placeCall(context: Context, contact: MaxContact): ToolResult {
        val number = contact.phoneNumber.filter { it.isDigit() || it == '+' }
        if (number.isEmpty()) return ToolResult(false, "${contact.name} ka number sahi nahi hai.")
        val uri = Uri.parse("tel:$number")

        val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        if (canCall) {
            try {
                context.startActivity(Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return ToolResult(true, "${contact.name} ko call laga raha hoon.", number)
            } catch (e: SecurityException) {
                // fall through to the dialer
            } catch (e: Exception) {
                return ToolResult(false, "${contact.name} ko call nahi laga paaya.")
            }
        }
        return try {
            context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolResult(false, "Call ki permission nahi hai, isliye ${contact.name} ka number dialer me khol diya. Call button aap dabao.", number)
        } catch (e: Exception) {
            ToolResult(false, "Dialer nahi khul paaya.")
        }
    }

    // ---- WhatsApp ---------------------------------------------------------------------------

    private fun whatsappPackage(context: Context): String? {
        val pm = context.packageManager
        return WHATSAPP_PACKAGES.firstOrNull { pm.getLaunchIntentForPackage(it) != null }
    }

    /**
     * Opens the chat with [message] pre-filled, then presses Send through MAX's Accessibility
     * service. MAX only reads the Send button — never chat contents. Reports "sent" only after the
     * Send button was really pressed and disappeared from the composer; if Accessibility is off
     * or anything fails, it says the chat is ready and the user must tap Send.
     */
    suspend fun sendWhatsApp(context: Context, contact: MaxContact, message: String): ToolResult {
        val pkg = whatsappPackage(context) ?: return ToolResult(false, "WhatsApp is phone me installed nahi hai.")
        val phone = normalizePhone(contact.phoneNumber, simCountryIso(context))
        if (phone.isEmpty()) return ToolResult(false, "${contact.name} ka number sahi nahi hai.")

        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(message)}")
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            return ToolResult(false, "${contact.name} ki WhatsApp chat nahi khul paayi.")
        }

        val service = MaxAccessibilityService.instance
        if (service == null) {
            return ToolResult(
                false,
                "${contact.name} ki chat khol di aur message likh diya. Send aap dabao, kyunki MAX ka Accessibility ON nahi hai."
            )
        }

        // Wait for WhatsApp to come up and show the Send button (only appears once text is in the box).
        val deadline = SystemClock.elapsedRealtime() + SEND_WAIT_MS
        var clicked = false
        while (SystemClock.elapsedRealtime() < deadline) {
            if (MaxAccessibilityService.currentActivePackage == pkg && service.clickSendButton(pkg)) {
                clicked = true
                break
            }
            delay(350)
        }
        if (!clicked) {
            return ToolResult(
                false,
                "Chat khuli hai par Send button nahi mila. Ho sakta hai number WhatsApp par na ho. Aap check karke Send dabao."
            )
        }
        delay(700)
        return if (!service.hasSendButton(pkg)) {
            ToolResult(true, "${contact.name} ko WhatsApp message bhej diya.", phone)
        } else {
            ToolResult(false, "Send dabaya par pakka nahi hua. WhatsApp me ek baar dekh lo.")
        }
    }

    // ---- Phone number helpers ---------------------------------------------------------------

    private val COUNTRY_CODES = mapOf(
        "in" to "91", "pk" to "92", "bd" to "880", "lk" to "94", "np" to "977",
        "ae" to "971", "sa" to "966", "qa" to "974", "kw" to "965", "om" to "968",
        "us" to "1", "ca" to "1", "gb" to "44", "au" to "61"
    )

    private fun simCountryIso(context: Context): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val iso = tm?.simCountryIso?.takeIf { it.isNotBlank() } ?: tm?.networkCountryIso.orEmpty()
        return iso.lowercase()
    }

    /**
     * WhatsApp links need the full international number as digits only. Contacts are often saved
     * without a country code, so add the SIM's country code when it is missing.
     */
    fun normalizePhone(raw: String, countryIso: String): String {
        var s = raw.trim().filter { it.isDigit() || it == '+' }
        if (s.startsWith("00")) s = "+" + s.drop(2)
        if (s.startsWith("+")) return s.drop(1).filter { it.isDigit() }
        val digits = s.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        val code = COUNTRY_CODES[countryIso.lowercase()] ?: return digits
        val local = digits.trimStart('0')
        return if (local.startsWith(code) && local.length >= code.length + 8) local else code + local
    }

    private const val SEND_WAIT_MS = 9000L
}
