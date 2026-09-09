package org.fossify.phone.activities

import android.os.Bundle
import android.view.LayoutInflater
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.R
import org.fossify.phone.cm.CmContact
import org.fossify.phone.cm.CmContactStore
import org.fossify.phone.databinding.ActivityCmContactBinding
import org.fossify.phone.databinding.ItemCmPhoneNumberBinding
import org.fossify.phone.extensions.startCallWithConfirmationCheck
import org.fossify.phone.helpers.CM_CONTACT_ID

/**
 * Read-only detail view for a single CM contact. Everything comes from the local
 * [CmContactStore] cache, so it works offline. Batch 3.
 */
class CmContactActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityCmContactBinding::inflate)

    private var contact: CmContact? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomSystem = listOf(binding.cmContactScrollview))
        setupMaterialScrollListener(binding.cmContactScrollview, binding.cmContactAppbar)

        ensureBackgroundThread {
            CmContactStore.ensureLoaded(this)
            val loaded = intent.getStringExtra(CM_CONTACT_ID)?.let { CmContactStore.findById(it) }
            runOnUiThread {
                if (loaded == null) {
                    toast(R.string.cm_contact_not_found)
                    finish()
                } else {
                    contact = loaded
                    bindContact(loaded)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.cmContactAppbar, NavigationIcon.Arrow)
        updateTextColors(binding.cmContactScrollview)
    }

    private fun bindContact(contact: CmContact) {
        val displayName = contact.fullName.ifBlank { contact.primaryNumber }
        binding.cmContactToolbar.title = displayName
        binding.cmContactName.text = displayName
        SimpleContactsHelper(this).loadContactImage(contact.photoUrl.orEmpty(), binding.cmContactPhoto, displayName)

        buildPhoneNumberRows(contact, displayName)

        bindCard(
            holder = binding.cmContactNoteHolder,
            valueView = binding.cmContactNote,
            value = contact.note,
        )
        bindCard(
            holder = binding.cmContactPropertyHolder,
            valueView = binding.cmContactProperty,
            value = contact.relatedProperty?.label,
        )
        bindCard(
            holder = binding.cmContactClientHolder,
            valueView = binding.cmContactClient,
            value = contact.relatedClient?.label,
        )

        updateTextColors(binding.cmContactScrollview)

        val primaryColor = getProperPrimaryColor()
        binding.cmContactNoteLabel.setTextColor(primaryColor)
        binding.cmContactPropertyLabel.setTextColor(primaryColor)
        binding.cmContactClientLabel.setTextColor(primaryColor)
    }

    private fun buildPhoneNumberRows(contact: CmContact, displayName: String) {
        val holder = binding.cmContactNumbersHolder
        holder.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val primaryColor = getProperPrimaryColor()

        contact.phoneNumbers.forEach { phone ->
            val row = ItemCmPhoneNumberBinding.inflate(inflater, holder, false)
            row.cmPhoneNumber.text = phone.number
            row.cmPhoneNumberLabel.text = phone.label.orEmpty()
            row.cmPhoneNumberLabel.beGoneIf(phone.label.isNullOrBlank())
            row.cmPhoneCall.applyColorFilter(primaryColor)
            row.root.setOnClickListener {
                startCallWithConfirmationCheck(phone.number, displayName)
            }
            holder.addView(row.root)
        }
    }

    private fun bindCard(
        holder: android.view.View,
        valueView: android.widget.TextView,
        value: String?,
    ) {
        holder.beGoneIf(value.isNullOrBlank())
        if (!value.isNullOrBlank()) {
            valueView.text = value
        }
    }
}
