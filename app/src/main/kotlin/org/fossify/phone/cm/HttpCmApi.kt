package org.fossify.phone.cm

import android.util.Log
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Real `cm-api` client. Active once a non-blank, non-"mock" base URL is set.
 *
 * Contract notes (see `docs/from-api.md`):
 *  - data calls live under `<baseUrl>/device/…`; `baseUrl` itself carries no path
 *  - error bodies are `{ "error": "UPPER_SNAKE_CODE" }` (a bare string)
 *  - any `2xx` is success (POSTs return `201`)
 *  - access token TTL is 1 h; on a `401` we refresh once (rotated refresh token,
 *    persisted via [onTokensRefreshed]) and retry the call
 */
class HttpCmApi(
    baseUrl: String,
    private val accessTokenProvider: () -> String?,
    private val refreshTokenProvider: () -> String? = { null },
    private val onTokensRefreshed: (RefreshResponse) -> Unit = {},
) : CmApi {

    private val base = baseUrl.trimEnd('/').toHttpUrl()

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    override fun pair(code: String): PairResponse {
        val body = json.encodeToString(PairRequest.serializer(), PairRequest(code))
        return call(
            request = jsonPost("device/pair", body),
            authenticated = false,
            deserializer = PairResponse.serializer(),
        )
    }

    override fun refresh(refreshToken: String): RefreshResponse {
        val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
        return call(
            request = jsonPost("device/refresh", body),
            authenticated = false,
            deserializer = RefreshResponse.serializer(),
        )
    }

    override fun unpairDevice() {
        call<Unit>(
            request = Request.Builder().url(path("device/pair")).delete().build(),
            authenticated = true,
            deserializer = null,
        )
    }

    override fun getContacts(sinceMillis: Long?): List<CmContact> {
        return call(
            request = Request.Builder().url(path("device/contacts")).get().build(),
            authenticated = true,
            deserializer = ListSerializer(CmContact.serializer()),
        )
    }

    override fun createContact(request: CreateContactRequest): CmContact {
        val body = json.encodeToString(CreateContactRequest.serializer(), request)
        return call(
            request = jsonPost("device/contacts", body),
            authenticated = true,
            deserializer = CmContact.serializer(),
        )
    }

    override fun addNote(contactId: String, note: String): CmContact {
        val body = json.encodeToString(AddNoteRequest.serializer(), AddNoteRequest(note))
        return call(
            request = jsonPost("device/contacts/$contactId/notes", body),
            authenticated = true,
            deserializer = CmContact.serializer(),
        )
    }

    private fun path(segments: String) = base.newBuilder().addPathSegments(segments).build()

    private fun jsonPost(segments: String, body: String) = Request.Builder()
        .url(path(segments))
        .post(body.toRequestBody(JSON_MEDIA_TYPE))
        .build()

    private fun <T> call(
        request: Request,
        authenticated: Boolean,
        deserializer: DeserializationStrategy<T>?,
    ): T {
        val prepared = if (authenticated) {
            val token = accessTokenProvider()
                ?: throw CmApiException(HTTP_UNAUTHORIZED, "Not paired", CmErrorCodes.DEVICE_SESSION_REVOKED)
            request.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            request
        }

        return execute(prepared, deserializer, allowRefresh = authenticated)
    }

    private fun <T> execute(
        request: Request,
        deserializer: DeserializationStrategy<T>?,
        allowRefresh: Boolean,
    ): T {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            Log.w(TAG, "${request.method} ${request.url} failed", e)
            throw CmApiException(NETWORK_ERROR, "Network error: ${e.message}", cause = e)
        } catch (e: RuntimeException) {
            // e.g. SecurityException without the INTERNET permission, or a
            // malformed request (IllegalState / IllegalArgument).
            Log.w(TAG, "${request.method} ${request.url} failed", e)
            throw CmApiException(NETWORK_ERROR, "Request failed: ${e.message}", cause = e)
        }

        response.use {
            val payload = it.body?.string().orEmpty()

            if (it.code == HTTP_UNAUTHORIZED && allowRefresh) {
                val freshAccessToken = tryRefresh()
                if (freshAccessToken != null) {
                    val retried = request.newBuilder()
                        .header("Authorization", "Bearer $freshAccessToken")
                        .build()
                    return execute(retried, deserializer, allowRefresh = false)
                }
            }

            if (!it.isSuccessful) {
                Log.w(TAG, "${request.method} ${request.url} -> ${it.code} $payload")
                throw CmApiException(
                    statusCode = it.code,
                    message = "CM API ${it.code}: $payload",
                    errorCode = parseErrorCode(payload),
                )
            }

            if (deserializer == null) {
                @Suppress("UNCHECKED_CAST")
                return Unit as T
            }

            return try {
                json.decodeFromString(deserializer, payload)
            } catch (e: Exception) {
                throw CmApiException(it.code, "Malformed CM response", cause = e)
            }
        }
    }

    /** POST /device/refresh once; on success persists the rotated tokens. */
    private fun tryRefresh(): String? {
        val refreshToken = refreshTokenProvider() ?: return null
        return try {
            val response = refresh(refreshToken)
            onTokensRefreshed(response)
            response.accessToken
        } catch (e: CmApiException) {
            null
        }
    }

    private fun parseErrorCode(payload: String): String? = try {
        json.decodeFromString(CmErrorBody.serializer(), payload).error?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val TAG = "CmApi"
        private const val CONNECT_TIMEOUT_SEC = 15L
        private const val READ_TIMEOUT_SEC = 30L
        private const val HTTP_UNAUTHORIZED = 401
        private const val NETWORK_ERROR = -1
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
