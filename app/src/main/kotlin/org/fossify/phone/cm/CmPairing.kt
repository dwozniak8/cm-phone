package org.fossify.phone.cm

import android.content.Context
import androidx.annotation.StringRes
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.R
import org.fossify.phone.extensions.config
import org.greenrobot.eventbus.EventBus
import java.net.HttpURLConnection

/**
 * Pairs the device with CM using a one-time code. Shared by the manual pairing
 * form ([org.fossify.phone.fragments.CmFragment]) and the `cmphone://pair`
 * auto-configuration link ([org.fossify.phone.activities.CmPairActivity]).
 */
object CmPairing {

    sealed class Result {
        /** [accountName] is the CM user name, or null when the server sent none. */
        class Success(val accountName: String?) : Result()
        class Failure(@StringRes val messageRes: Int) : Result()
    }

    /**
     * Runs on a background thread; [onResult] is called on that thread too.
     * [code] is upper-cased here - codes are issued upper-case and the server
     * is case-sensitive.
     */
    fun pair(context: Context, serviceUrl: String, code: String, onResult: (Result) -> Unit) {
        val appContext = context.applicationContext
        val url = serviceUrl.trim()
        appContext.config.cmPairingUrl = url

        ensureBackgroundThread {
            val result = try {
                // pair against the given address; the response tells us which
                // endpoints to use from now on.
                val response = CmClient.getApi(appContext, url).pair(code.trim().uppercase())
                appContext.config.cmBaseUrl = response.baseUrl?.takeIf { it.isNotBlank() } ?: url
                response.appUrl?.takeIf { it.isNotBlank() }?.let { appContext.config.cmAppUrl = it }
                appContext.cmAuthStore.save(response)
                EventBus.getDefault().post(CmPairingStateChangedEvent(paired = true))
                Result.Success(appContext.cmAuthStore.userName?.takeIf { it.isNotBlank() })
            } catch (e: CmApiException) {
                // HttpCmApi wraps network / parsing errors into CmApiException too
                val messageRes = if (e.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    R.string.cm_pairing_failed
                } else {
                    R.string.cm_pairing_network_error
                }
                Result.Failure(messageRes)
            }
            onResult(result)
        }
    }
}
