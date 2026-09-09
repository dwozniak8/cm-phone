package org.fossify.phone.fragments

import android.content.Context
import android.util.AttributeSet
import org.fossify.commons.extensions.applyColorFilter
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
import org.fossify.phone.cm.CmApiException
import org.fossify.phone.cm.CmClient
import org.fossify.phone.cm.CmContactStore
import org.fossify.phone.cm.CmContactsUpdatedEvent
import org.fossify.phone.cm.CmPairingStateChangedEvent
import org.fossify.phone.cm.cmAuthStore
import org.fossify.phone.databinding.FragmentCmBinding
import org.fossify.phone.databinding.FragmentCmLayoutBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.interfaces.RefreshItemsListener
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Replaces the former Favorites tab. Batch 1: pairing (enter code) and the
 * paired/empty state. The contact list itself lands in Batch 2.
 */
class CmFragment(context: Context, attributeSet: AttributeSet) :
    MyViewPagerFragment<MyViewPagerFragment.CmInnerBinding>(context, attributeSet), RefreshItemsListener {

    private lateinit var binding: FragmentCmLayoutBinding
    private var pairing = false

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

        CmContactStore.ensureLoaded(context)
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
        binding.cmPlaceholder.setTextColor(textColor)
        binding.cmPairingCode.setTextColor(textColor)
        binding.cmGetCode.setTextColor(properPrimaryColor)

        binding.cmPairButton.background?.applyColorFilter(properPrimaryColor)
        binding.cmPairButton.setTextColor(properPrimaryColor.getContrastColor())
    }

    override fun refreshItems(invalidate: Boolean, callback: (() -> Unit)?) {
        CmContactStore.ensureLoaded(context)
        updateState()
        callback?.invoke()
    }

    override fun onSearchClosed() {}

    override fun onSearchQueryChanged(text: String) {}

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onContactsUpdated(event: CmContactsUpdatedEvent) {
        updateState()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPairingStateChanged(event: CmPairingStateChangedEvent) {
        updateState()
    }

    private fun updateState() {
        val paired = context.cmAuthStore.isPaired

        binding.cmPairingHolder.beVisibleIf(!paired && !pairing)
        binding.cmPairingProgress.beVisibleIf(pairing)
        // Batch 2 renders CmContactStore.getAll() into cmList; until then the
        // paired state is just the placeholder.
        binding.cmList.beGone()
        binding.cmPlaceholder.beVisibleIf(paired)
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
