package org.fossify.phone.cm

import android.content.Context

/** Lazily-built encrypted auth store. Cheap to recreate; mirrors `Context.config`. */
val Context.cmAuthStore: CmAuthStore
    get() = CmAuthStore(applicationContext)

/**
 * Normalises a phone number to the key used by [CmContactStore]'s index: digits
 * only, last 9 kept. Matches `RecentsHelper.COMPARABLE_PHONE_NUMBER_LENGTH`, so a
 * "+48 600 100 200" in CM matches a "600100200" dialled locally.
 */
fun String.toCmNumberKey(): String {
    val digits = filter { it.isDigit() }
    return if (digits.length <= CM_NUMBER_KEY_LENGTH) digits else digits.takeLast(CM_NUMBER_KEY_LENGTH)
}

private const val CM_NUMBER_KEY_LENGTH = 9
