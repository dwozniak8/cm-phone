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

@Serializable
data class AddNoteRequest(
    val note: String,
)

/** Thrown by [CmApi] implementations on any non-success outcome. */
class CmApiException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
