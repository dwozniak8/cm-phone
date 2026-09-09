package org.fossify.phone.cm

import android.content.Context
import org.fossify.phone.extensions.config

/**
 * Picks the right [CmApi] for the current configuration: the in-app [FakeCmApi]
 * when no CM base URL is set (default / debug), otherwise a real [HttpCmApi].
 */
object CmClient {

    private val fake by lazy { FakeCmApi() }

    fun getApi(context: Context): CmApi {
        val baseUrl = context.config.cmBaseUrl.trim()
        return if (baseUrl.isEmpty() || baseUrl.equals("mock", ignoreCase = true)) {
            fake
        } else {
            HttpCmApi(
                baseUrl = baseUrl,
                accessTokenProvider = { context.cmAuthStore.accessToken },
            )
        }
    }

    fun isUsingFake(context: Context): Boolean {
        val baseUrl = context.config.cmBaseUrl.trim()
        return baseUrl.isEmpty() || baseUrl.equals("mock", ignoreCase = true)
    }
}
