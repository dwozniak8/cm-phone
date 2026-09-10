package org.fossify.phone.cm

import android.content.Context
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.extensions.config

private const val MOCK_URL = "mock"

/**
 * Picks the right [CmApi] for a given base URL: the in-app [FakeCmApi] when the
 * URL is blank or "mock" (default / demo), otherwise a real [HttpCmApi].
 *
 * Pairing calls pass the address the user typed on the pairing screen; every
 * other call uses [org.fossify.phone.helpers.Config.cmBaseUrl], which the
 * pairing response fills in.
 */
object CmClient {

    private val fake by lazy { FakeCmApi() }

    fun getApi(context: Context, baseUrl: String = context.config.cmBaseUrl): CmApi {
        val url = baseUrl.trim()
        if (isFakeUrl(url)) {
            return fake
        }

        val appContext = context.applicationContext
        return HttpCmApi(
            baseUrl = url,
            accessTokenProvider = { appContext.cmAuthStore.accessToken },
            refreshTokenProvider = { appContext.cmAuthStore.refreshToken },
            onTokensRefreshed = { appContext.cmAuthStore.save(it) },
        )
    }

    /**
     * Fire-and-forget `DELETE /device/pair`. Snapshots the current token
     * synchronously (call this *before* clearing [CmAuthStore]) and does the
     * network on a background thread; failures are ignored.
     */
    fun revokeDeviceAsync(context: Context) {
        val baseUrl = context.config.cmBaseUrl.trim()
        val accessToken = context.cmAuthStore.accessToken
        if (isFakeUrl(baseUrl) || accessToken.isNullOrBlank()) {
            return
        }

        ensureBackgroundThread {
            try {
                HttpCmApi(baseUrl = baseUrl, accessTokenProvider = { accessToken }).unpairDevice()
            } catch (e: CmApiException) {
                // ignored - the device is unpaired locally regardless
            }
        }
    }

    private fun isFakeUrl(url: String): Boolean = url.isEmpty() || url.equals(MOCK_URL, ignoreCase = true)
}
