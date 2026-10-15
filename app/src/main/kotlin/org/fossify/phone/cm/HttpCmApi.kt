package org.fossify.phone.cm

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Real `cm-api` client. Not exercised until a base URL is set in Settings.
 *
 * Auth: every call except [pair] sends `Authorization: Bearer <accessToken>`.
 * On a 401 the caller ([CmSyncManager] / dialogs) is expected to surface the
 * "unpaired" state; automatic refresh-and-retry is added in a later batch.
 */
class HttpCmApi(
    baseUrl: String,
    private val accessTokenProvider: () -> String?,
) : CmApi {

    private val base = baseUrl.trimEnd('/').toHttpUrl()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    override fun pair(code: String): PairResponse {
        val body = json.encodeToString(PairRequest.serializer(), PairRequest(code))
        return request(
            builder = Request.Builder()
                .url(base.newBuilder().addPathSegments("device/pair").build())
                .post(body.toRequestBody(JSON_MEDIA_TYPE)),
            authenticated = false,
            deserializer = PairResponse.serializer(),
        )
    }

    override fun refresh(refreshToken: String): RefreshResponse {
        val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
        return request(
            builder = Request.Builder()
                .url(base.newBuilder().addPathSegments("device/refresh").build())
                .post(body.toRequestBody(JSON_MEDIA_TYPE)),
            authenticated = false,
            deserializer = RefreshResponse.serializer(),
        )
    }

    override fun getContacts(sinceMillis: Long?): List<CmContact> {
        val url = base.newBuilder().addPathSegments("contacts").apply {
            if (sinceMillis != null) addQueryParameter("since", sinceMillis.toString())
        }.build()

        return request(
            builder = Request.Builder().url(url).get(),
            authenticated = true,
            deserializer = kotlinx.serialization.builtins.ListSerializer(CmContact.serializer()),
        )
    }

    override fun createContact(request: CreateContactRequest): CmContact {
        val body = json.encodeToString(CreateContactRequest.serializer(), request)
        return request(
            builder = Request.Builder()
                .url(base.newBuilder().addPathSegments("contacts").build())
                .post(body.toRequestBody(JSON_MEDIA_TYPE)),
            authenticated = true,
            deserializer = CmContact.serializer(),
        )
    }

    override fun addNote(contactId: String, note: String): CmContact {
        val body = json.encodeToString(AddNoteRequest.serializer(), AddNoteRequest(note))
        return request(
            builder = Request.Builder()
                .url(base.newBuilder().addPathSegments("contacts/$contactId/notes").build())
                .post(body.toRequestBody(JSON_MEDIA_TYPE)),
            authenticated = true,
            deserializer = CmContact.serializer(),
        )
    }

    private fun <T> request(
        builder: Request.Builder,
        authenticated: Boolean,
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
    ): T {
        if (authenticated) {
            val token = accessTokenProvider()
                ?: throw CmApiException(statusCode = 401, message = "Not paired")
            builder.header("Authorization", "Bearer $token")
        }

        val response = try {
            client.newCall(builder.build()).execute()
        } catch (e: IOException) {
            throw CmApiException(statusCode = -1, message = "Network error: ${e.message}", cause = e)
        } catch (e: RuntimeException) {
            // e.g. SecurityException when the INTERNET permission is missing, or
            // IllegalState/IllegalArgument from a malformed request.
            throw CmApiException(statusCode = -1, message = "Request failed: ${e.message}", cause = e)
        }

        response.use {
            val payload = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                throw CmApiException(statusCode = it.code, message = "CM API ${it.code}: $payload")
            }

            return try {
                json.decodeFromString(deserializer, payload)
            } catch (e: Exception) {
                throw CmApiException(statusCode = it.code, message = "Malformed CM response", cause = e)
            }
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
