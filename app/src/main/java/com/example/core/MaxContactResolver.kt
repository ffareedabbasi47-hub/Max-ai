package com.example.core

import android.content.Context
import android.provider.ContactsContract

data class MaxContact(val name: String, val phoneNumber: String)

/**
 * Resolves a spoken/typed contact name to a phone number — fuzzy-matched via [FuzzyMatch].
 *
 * MAX already declares READ_CONTACTS in its manifest but nothing in the codebase used it —
 * `SystemControlManager.makeCall` stripped whatever string the AI returned down to digits
 * (falling back to a hardcoded fake number if that came up empty), and
 * `sendWhatsAppMessage` never resolved the recipient at all, just shared the message text via
 * a generic chooser. This is what actually puts that permission to use.
 */
object MaxContactResolver {

    private var cache: List<MaxContact>? = null

    fun invalidateCache() {
        cache = null
    }

    fun getContacts(context: Context, forceRefresh: Boolean = false): List<MaxContact> {
        cache?.let { if (!forceRefresh) return it }

        val contacts = mutableListOf<MaxContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, null, null, null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (nameIdx < 0 || numberIdx < 0) return@use
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    val number = cursor.getString(numberIdx) ?: continue
                    contacts.add(MaxContact(name, number))
                }
            }
        }
        // Missing permission or any query failure just yields an empty list — callers report
        // "contact not found" rather than crashing or silently guessing.

        cache = contacts
        return contacts
    }

    fun match(context: Context, spokenName: String): FuzzyMatch.MatchOutcome<MaxContact> =
        FuzzyMatch.match(spokenName, getContacts(context)) { it.name }
}
