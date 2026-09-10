package org.fossify.phone.cm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A contact pulled from CM. Stored locally in `cm_contacts.json` and used for all
 * offline number lookups (incoming calls, dialer, call history).
 */
@Serializable
data class CmContact(
    val id: String,
    val firstName: String = "",
    val lastName: String = "",
    val phoneNumbers: List<CmPhoneNumber> = emptyList(),
    val photoUrl: String? = null,
    val notes: List<String> = emptyList(),
    val relatedProperties: List<CmRef> = emptyList(),
    val relatedClients: List<CmRef> = emptyList(),
    val updatedAt: Long = 0L,
) {
    val fullName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").trim()

    val primaryNumber: String
        get() = phoneNumbers.firstOrNull()?.number.orEmpty()
}

@Serializable
data class CmPhoneNumber(
    val number: String,
    val label: String? = null,
)

/**
 * A lightweight reference to a related CM record (property or client). [id] is
 * used to build the deep link into the CM web app; [label] is what we show.
 */
@Serializable
data class CmRef(
    val id: String,
    val label: String,
)

@Serializable
data class CmUser(
    val id: String,
    val name: String,
)

@Serializable
data class PairRequest(
    val code: String,
)

@Serializable
data class PairResponse(
    val accessToken: String,
    val refreshToken: String? = null,
    @SerialName("expiresInSec") val expiresInSec: Long = 0L,
    val user: CmUser? = null,
    // endpoints the app should use from now on - the pairing service resolves
    // and returns them, so they are not configured by hand.
    val baseUrl: String? = null,
    val appUrl: String? = null,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class RefreshResponse(
    val accessToken: String,
    val refreshToken: String? = null,
    @SerialName("expiresInSec") val expiresInSec: Long = 0L,
)

@Serializable
data class CreateContactRequest(
    val firstName: String,
    val lastName: String,
    val note: String? = null,
    val phoneNumbers: List<CmPhoneNumber> = emptyList(),
)

object NoteTargetType {
    const val CONTACT = "contact"
    const val BUILD = "build" // a related property / "nieruchomość"
    const val CLIENT = "client" // a related prospect / "klient poszukujący"
}

/** Where a note should land. `null` target ⇒ the contact itself. */
@Serializable
data class NoteTarget(
    val type: String,
    val id: String,
)

@Serializable
data class AddNoteRequest(
    val note: String,
    val target: NoteTarget? = null,
)

/** Error body shape: `{ "error": "UPPER_SNAKE_CASE_CODE" }`. */
@Serializable
data class CmErrorBody(
    val error: String? = null,
)

object CmErrorCodes {
    const val INVALID_PAIRING_CODE = "INVALID_PAIRING_CODE"
    const val INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN"
    const val DEVICE_SESSION_REVOKED = "DEVICE_SESSION_REVOKED"
    const val CONTACT_ALREADY_EXISTS = "CONTACT_ALREADY_EXISTS"
    const val INVALID_PHONE_NUMBER = "INVALID_PHONE_NUMBER"
    const val ITEM_NOT_FOUND = "ITEM_NOT_FOUND"
}

/** Thrown by [CmApi] implementations on any non-success outcome. */
class CmApiException(
    val statusCode: Int,
    message: String,
    val errorCode: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
