package org.fossify.phone.activities

import android.content.Intent
import android.os.Bundle
import org.fossify.commons.extensions.toast
import org.fossify.phone.R
import org.fossify.phone.cm.CmClient
import org.fossify.phone.cm.CmPairing
import org.fossify.phone.cm.cmAuthStore
import org.fossify.phone.extensions.config
import org.fossify.phone.helpers.CM_PAIR_PARAM_CODE
import org.fossify.phone.helpers.CM_PAIR_PARAM_SERVICE
import org.fossify.phone.helpers.OPEN_CM_TAB

/**
 * Handles the CM web app's "Autokonfiguracja" link
 * (`cmphone://pair?code=<code>&service=<api url>`): pairs in the background,
 * then opens the CM tab of [MainActivity]. Has no UI of its own (transparent).
 */
class CmPairActivity : SimpleActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return

        val data = intent.data
        val code = data?.getQueryParameter(CM_PAIR_PARAM_CODE)?.trim().orEmpty()
        val service = data?.getQueryParameter(CM_PAIR_PARAM_SERVICE)?.trim().orEmpty()
        if (code.isEmpty() || service.isEmpty()) {
            toast(R.string.cm_autoconfig_invalid_link)
            openCmTab()
            return
        }

        // when this replaces an existing pairing, the old device token is
        // revoked only once the new one is in place
        val previousBaseUrl = config.cmBaseUrl
        val previousAccessToken = cmAuthStore.accessToken.takeIf { cmAuthStore.isPaired }

        toast(R.string.cm_autoconfig_pairing)
        CmPairing.pair(this, service, code) { result ->
            runOnUiThread {
                when (result) {
                    is CmPairing.Result.Success -> {
                        CmClient.revokeDeviceAsync(previousBaseUrl, previousAccessToken)
                        val account = result.accountName ?: getString(R.string.cm_tab)
                        toast(getString(R.string.cm_pairing_success, account))
                    }

                    is CmPairing.Result.Failure -> toast(result.messageRes)
                }
                openCmTab()
            }
        }
    }

    private fun openCmTab() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(OPEN_CM_TAB, true)
                .addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                )
        )
        finish()
    }
}
