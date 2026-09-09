package org.fossify.phone.fragments

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.launchViewIntent
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.value
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.R
import org.fossify.phone.activities.CmContactActivity
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.adapters.CmContactsAdapter
import org.fossify.phone.cm.CmApiException
import org.fossify.phone.cm.CmClient
import org.fossify.phone.cm.CmContact
import org.fossify.phone.cm.CmContactStore
import org.fossify.phone.cm.CmContactsUpdatedEvent
import org.fossify.phone.cm.CmPairingStateChangedEvent
import org.fossify.phone.cm.CmSyncManager
import org.fossify.phone.cm.cmAuthStore
import org.fossify.phone.databinding.FragmentCmBinding
import org.fossify.phone.databinding.FragmentCmLayoutBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.helpers.CM_CONTACT_ID
import org.fossify.phone.interfaces.RefreshItemsListener
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Replaces the former Favorites tab.
 *  - unpaired: enter-code pairing prompt
 *  - paired + empty: placeholder that also triggers a sync when tapped
 *  - paired + populated: the CM contact list (from [CmContactStore], offline)
 *
 * A manual sync is also reachable from the overflow menu ("Zaktualizuj bazę",
 * handled in MainActivity -> [refreshFromServer]).
 */
class CmFragment(context: Context, attributeSet: AttributeSet) :
    MyViewPagerFragment<MyViewPagerFragment.CmInnerBinding>(context, attributeSet), RefreshItemsListener {

    private lateinit var binding: FragmentCmLayoutBinding
    private var pairing = false
    private var syncing = false
    private var allContacts = listOf<CmContact>()

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentCmLayoutBinding.bind(FragmentCmBinding.bind(this).cmFragment)
        innerBinding = CmInnerBinding(binding)
    }

    override fun setupFragment() {
        binding.cmPairButton.setOnClickListener { pair() }
        binding.cmGetCode.apply {
            underlineText()
            setOnClickListener { activity?.launchViewIntent(activity!!.config.cmPairingUrl) }
        }
        binding.cmPlaceholder.setOnClickListener {
            if (context.cmAuthStore.isPaired) refreshFromServer()
        }

        CmContactStore.ensureLoaded(context)
        allContacts = CmContactStore.getAll()
        updateState()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        binding.cmPairingPrompt.setTextColor(textColor)
        binding.cmPairingCode.setTextColor(textColor)
        binding.cmPlaceholder.setTextColor(textColor)
        binding.cmGetCode.setTextColor(properPrimaryColor)

        binding.cmPairButton.background?.applyColorFilter(properPrimaryColor)
        binding.cmPairButton.setTextColor(properPrimaryColor.getContrastColor())

        (binding.cmList.adapter as? CmContactsAdapter)?.updateTextColor(textColor)
    }

    override fun refreshItems(invalidate: Boolean, callback: (() -> Unit)?) {
        CmContactStore.ensureLoaded(context)
        allContacts = CmContactStore.getAll()
        updateState()
        callback?.invoke()
    }

    /** Wired to the "Zaktualizuj bazę" overflow item and the empty-state tap. */
    fun refreshFromServer() {
        if (syncing || !context.cmAuthStore.isPaired) return
        val activity = activity ?: return

        syncing = true
        activity.toast(R.string.cm_updating_database)
        CmSyncManager.syncNow(activity) { success ->
            activity.runOnUiThread {
                syncing = false
                if (!success) {
                    activity.toast(R.string.cm_update_failed)
                }
                // a successful sync also posts CmContactsUpdatedEvent -> onContactsUpdated
            }
        }
    }

    override fun onSearchClosed() {
        (binding.cmList.adapter as? CmContactsAdapter)?.updateItems(allContacts)
    }

    override fun onSearchQueryChanged(text: String) {
        val query = text.trim()
        val queryDigits = query.filter(Char::isDigit)
        val filtered = if (query.isEmpty()) {
            allContacts
        } else {
            allContacts.filter { contact ->
                contact.fullName.contains(query, ignoreCase = true) ||
                    (queryDigits.isNotEmpty() && contact.phoneNumbers.any { it.number.filter(Char::isDigit).contains(queryDigits) })
            }
        }
        (binding.cmList.adapter as? CmContactsAdapter)?.updateItems(filtered, query)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onContactsUpdated(event: CmContactsUpdatedEvent) {
        allContacts = CmContactStore.getAll()
        updateState()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPairingStateChanged(event: CmPairingStateChangedEvent) {
        if (!event.paired) {
            allContacts = emptyList()
        }
        updateState()
    }

    private fun updateState() {
        val paired = context.cmAuthStore.isPaired
        val hasContacts = allContacts.isNotEmpty()

        binding.cmPairingHolder.beVisibleIf(!paired && !pairing)
        binding.cmPairingProgress.beVisibleIf(pairing)
        binding.cmList.beVisibleIf(paired && hasContacts)
        binding.cmPlaceholder.beVisibleIf(paired && !hasContacts)

        if (paired && hasContacts) {
            renderList()
        }
    }

    private fun renderList() {
        val adapter = binding.cmList.adapter as? CmContactsAdapter
        if (adapter == null) {
            binding.cmList.adapter = CmContactsAdapter(
                activity = activity as SimpleActivity,
                contacts = allContacts,
                recyclerView = binding.cmList,
                itemClick = { item ->
                    val cmContact = item as CmContact
                    context.startActivity(
                        Intent(context, CmContactActivity::class.java)
                            .putExtra(CM_CONTACT_ID, cmContact.id)
                    )
                },
            )
            if (context.areSystemAnimationsEnabled) {
                binding.cmList.scheduleLayoutAnimation()
            }
        } else {
            adapter.updateItems(allContacts)
        }
    }

    private fun pair() {
        if (pairing) return

        val code = binding.cmPairingCode.value.trim()
        if (code.isEmpty()) {
            activity?.toast(R.string.cm_pairing_code_hint)
            return
        }

        val activity = activity ?: return
        pairing = true
        binding.cmPairButton.beGone()
        updateState()

        ensureBackgroundThread {
            try {
                val response = CmClient.getApi(activity).pair(code)
                activity.cmAuthStore.save(response)
                EventBus.getDefault().post(CmPairingStateChangedEvent(paired = true))

                activity.runOnUiThread {
                    pairing = false
                    binding.cmPairButton.beVisible()
                    binding.cmPairingCode.setText("")
                    activity.toast(activity.getString(R.string.cm_pairing_success, activity.cmAuthStore.userName.orEmpty()))
                    updateState()
                    refreshFromServer()
                }
            } catch (e: CmApiException) {
                activity.runOnUiThread {
                    pairing = false
                    binding.cmPairButton.beVisible()
                    activity.toast(R.string.cm_pairing_failed)
                    updateState()
                }
            }
        }
    }
}
