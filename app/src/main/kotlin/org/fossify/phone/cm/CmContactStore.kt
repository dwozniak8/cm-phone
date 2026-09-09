package org.fossify.phone.cm

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The single source of truth for CM contacts on the device. Persists to
 * `cm_contacts.json` in the app files dir and keeps an in-memory number index so
 * every lookup (incoming call, dialer, call history) is synchronous and offline.
 *
 * All public methods are safe to call from any thread; disk work is small
 * (~200 rows) and guarded by [lock].
 */
object CmContactStore {

    private const val FILE_NAME = "cm_contacts.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val lock = Any()
    private val listSerializer = ListSerializer(CmContact.serializer())

    @Volatile
    private var contacts: List<CmContact> = emptyList()

    /** number-key -> contact. A contact with multiple numbers appears under each key. */
    @Volatile
    private var index: Map<String, CmContact> = emptyMap()

    @Volatile
    var isLoaded: Boolean = false
        private set

    fun ensureLoaded(context: Context) {
        if (!isLoaded) load(context)
    }

    fun load(context: Context) {
        synchronized(lock) {
            val file = fileFor(context)
            contacts = if (file.exists()) {
                runCatching { json.decodeFromString(listSerializer, file.readText()) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            index = buildIndex(contacts)
            isLoaded = true
        }
    }

    fun replaceAll(context: Context, newContacts: List<CmContact>) {
        synchronized(lock) {
            contacts = newContacts
            index = buildIndex(newContacts)
            isLoaded = true
            persist(context)
        }
    }

    fun upsert(context: Context, contact: CmContact) {
        synchronized(lock) {
            val merged = contacts.filterNot { it.id == contact.id } + contact
            contacts = merged.sortedBy { it.fullName.lowercase() }
            index = buildIndex(contacts)
            persist(context)
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            contacts = emptyList()
            index = emptyMap()
            fileFor(context).delete()
        }
    }

    fun getAll(): List<CmContact> = contacts

    fun findByNumber(number: String?): CmContact? {
        if (number.isNullOrBlank()) return null
        val key = number.toCmNumberKey()
        if (key.isEmpty()) return null
        return index[key]
    }

    fun isInCm(number: String?): Boolean = findByNumber(number) != null

    private fun buildIndex(source: List<CmContact>): Map<String, CmContact> {
        val map = HashMap<String, CmContact>()
        source.forEach { contact ->
            contact.phoneNumbers.forEach { phone ->
                val key = phone.number.toCmNumberKey()
                if (key.isNotEmpty()) {
                    map.putIfAbsent(key, contact)
                }
            }
        }
        return map
    }

    private fun persist(context: Context) {
        runCatching {
            fileFor(context).writeText(json.encodeToString(listSerializer, contacts))
        }
    }

    private fun fileFor(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)
}
