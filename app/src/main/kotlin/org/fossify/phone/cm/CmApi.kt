package org.fossify.phone.cm

/**
 * The contract the app needs from `cm-api`. Blocking on purpose - every call site
 * already runs on a background thread (`ensureBackgroundThread`), matching the
 * rest of the codebase. Implementations throw [CmApiException] on failure.
 *
 * Two implementations exist:
 *  - [HttpCmApi]  - real, OkHttp + kotlinx.serialization
 *  - [FakeCmApi]  - canned data, used until `cm-api` exists (blank base URL)
 */
interface CmApi {

    /** Exchanges a pairing code shown in the CM web app for access tokens. */
    fun pair(code: String): PairResponse

    /** Exchanges a refresh token for a fresh access token. */
    fun refresh(refreshToken: String): RefreshResponse

    /**
     * Full contact list for the paired user, or the delta since [sinceMillis]
     * when the server supports it.
     */
    fun getContacts(sinceMillis: Long? = null): List<CmContact>

    /** Creates a new CM contact and returns it as stored server-side. */
    fun createContact(request: CreateContactRequest): CmContact

    /** Appends a note to an existing CM contact and returns the updated contact. */
    fun addNote(contactId: String, note: String): CmContact
}
