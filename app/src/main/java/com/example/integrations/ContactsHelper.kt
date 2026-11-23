package com.example.integrations

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

data class ContactMatch(
    val name: String,
    val phoneNumber: String,
    val label: String
)

object ContactsHelper {
    private const val TAG = "ContactsHelper"

    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Searches device contacts ephemerally for names matching any query token.
     * Results are never saved to long-term storage (data minimization).
     */
    fun searchContacts(
        context: Context,
        query: String,
        maxResults: Int = 5
    ): List<ContactMatch> {
        if (!hasContactsPermission(context)) return emptyList()
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return emptyList()

        val tokens = cleaned.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }
            .distinct()
        if (tokens.isEmpty()) return emptyList()

        val matches = linkedMapOf<String, ContactMatch>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)

                var scanned = 0
                while (cursor.moveToNext() && scanned < 400 && matches.size < maxResults) {
                    scanned++
                    val name = if (nameIdx != -1) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                    val rawNumber = if (numIdx != -1) cursor.getString(numIdx)?.trim().orEmpty() else ""
                    if (name.isEmpty() || rawNumber.isEmpty()) continue

                    val lowerName = name.lowercase()
                    val matched = lowerName.contains(cleaned.lowercase()) ||
                        tokens.any { token -> lowerName.contains(token) }

                    if (matched) {
                        val phoneType = if (typeIdx != -1) cursor.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                        val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                            context.resources,
                            phoneType,
                            ""
                        ).toString().ifBlank { "Phone" }
                        val dedupKey = "${name.lowercase()}_${rawNumber.filter { it.isDigit() || it == '+' }}"
                        matches.putIfAbsent(
                            dedupKey,
                            ContactMatch(name = name, phoneNumber = rawNumber, label = label)
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Contact query failed", e)
        }

        return matches.values.sortedByDescending {
            it.name.equals(cleaned, ignoreCase = true) ||
                it.name.startsWith(cleaned, ignoreCase = true)
        }
    }

    /**
     * Opens the native Phone dialer pre-filled with the selected phone number.
     * Uses ACTION_DIAL so the user confirms the call directly without needing restricted CALL_PHONE permission.
     */
    fun openNativeDialer(context: Context, phoneNumber: String): Boolean {
        val cleaned = phoneNumber.trim().filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (cleaned.isEmpty()) return false
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(cleaned)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch native dialer", e)
            false
        }
    }
}
