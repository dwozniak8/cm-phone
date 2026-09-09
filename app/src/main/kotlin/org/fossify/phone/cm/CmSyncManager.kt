package org.fossify.phone.cm

import android.content.Context
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.extensions.config
import org.fossify.phone.helpers.CM_SYNC_INTERVAL_MS
import org.greenrobot.eventbus.EventBus

/**
 * Pulls the CM contact list into [CmContactStore]. Called opportunistically from
 * `MainActivity.onResume` (stale check) and explicitly from the "Zaktualizuj bazę"
 * button. No background scheduler - lookups always read the cache, and a few
 * hours of staleness is acceptable per the design.
 */
object CmSyncManager {

    @Volatile
    private var syncing = false

    /** Syncs only if paired and the cache is older than [CM_SYNC_INTERVAL_MS]. */
    fun syncIfStale(context: Context) {
        if (!context.cmAuthStore.isPaired) return
        val age = System.currentTimeMillis() - context.config.cmLastSyncTS
        if (age < CM_SYNC_INTERVAL_MS) return
        syncNow(context, null)
    }

    /**
     * Fetches the full contact list and replaces the local cache. [callback] runs
     * on a background thread with `true` on success.
     */
    fun syncNow(context: Context, callback: ((Boolean) -> Unit)?) {
        if (!context.cmAuthStore.isPaired) {
            callback?.invoke(false)
            return
        }
        if (syncing) {
            callback?.invoke(false)
            return
        }

        syncing = true
        val appContext = context.applicationContext
        ensureBackgroundThread {
            val success = try {
                val contacts = CmClient.getApi(appContext).getContacts(sinceMillis = null)
                CmContactStore.replaceAll(appContext, contacts)
                appContext.config.cmLastSyncTS = System.currentTimeMillis()
                EventBus.getDefault().post(CmContactsUpdatedEvent())
                true
            } catch (e: CmApiException) {
                if (e.statusCode == HTTP_UNAUTHORIZED) {
                    appContext.cmAuthStore.clear()
                    EventBus.getDefault().post(CmPairingStateChangedEvent(paired = false))
                }
                false
            } catch (e: Exception) {
                false
            } finally {
                syncing = false
            }

            callback?.invoke(success)
        }
    }

    private const val HTTP_UNAUTHORIZED = 401
}
