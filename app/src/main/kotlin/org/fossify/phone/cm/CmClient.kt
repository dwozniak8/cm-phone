package org.fossify.phone.cm

import android.content.Context
import org.fossify.phone.extensions.config

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
        return if (isFakeUrl(url)) {
            fake
        } else {
            HttpCmApi(
                baseUrl = url,
                accessTokenProvider = { context.cmAuthStore.accessToken },
            )
        }
    }

    private fun isFakeUrl(url: String): Boolean = url.isEmpty() || url.equals("mock", ignoreCase = true)
}
