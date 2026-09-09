package org.fossify.phone.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.launchViewIntent
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.R
import org.fossify.phone.cm.CmContact
import org.fossify.phone.cm.CmContactStore
import org.fossify.phone.cm.CmRef
import org.fossify.phone.databinding.ActivityCmContactBinding
import org.fossify.phone.databinding.ItemCmNoteBinding
import org.fossify.phone.databinding.ItemCmPhoneNumberBinding
import org.fossify.phone.databinding.ItemCmRelatedBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.startCallWithConfirmationCheck
import org.fossify.phone.helpers.CM_CLIENT_PATH
import org.fossify.phone.helpers.CM_CONTACT_ID
import org.fossify.phone.helpers.CM_PROPERTY_PATH

/**
 * Read-only detail view for a single CM contact. Everything comes from the local
 * [CmContactStore] cache, so it works offline.
 *
 * Notes, related properties and related prospects are three collapsible sections
 * titled "<name> (<count>)"; opening one closes the others. Every property /
 * prospect row has an "open in CM" button that deep-links into the CM web app.
 */
class CmContactActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityCmContactBinding::inflate)

    private enum class Section { NOTES, PROPERTIES, CLIENTS }

    private var contact: CmContact? = null
    private var expandedSection: Section? = null

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

        populateNotes(contact.notes)
        populateRelated(binding.cmContactPropertyList, contact.relatedProperties, CM_PROPERTY_PATH)
        populateRelated(binding.cmContactClientList, contact.relatedClients, CM_CLIENT_PATH)

        setupSection(
            binding.cmContactNoteHolder, binding.cmContactNoteHeader, binding.cmContactNoteLabel,
            R.string.cm_detail_notes, contact.notes.size, Section.NOTES,
        )
        setupSection(
            binding.cmContactPropertyHolder, binding.cmContactPropertyHeader, binding.cmContactPropertyLabel,
            R.string.cm_detail_properties, contact.relatedProperties.size, Section.PROPERTIES,
        )
        setupSection(
            binding.cmContactClientHolder, binding.cmContactClientHeader, binding.cmContactClientLabel,
            R.string.cm_detail_clients, contact.relatedClients.size, Section.CLIENTS,
        )

        expandedSection = when {
            contact.notes.isNotEmpty() -> Section.NOTES
            contact.relatedProperties.isNotEmpty() -> Section.PROPERTIES
            contact.relatedClients.isNotEmpty() -> Section.CLIENTS
            else -> null
        }
        applyExpansion(animate = false)

        updateTextColors(binding.cmContactScrollview)

        val primaryColor = getProperPrimaryColor()
        binding.cmContactNoteLabel.setTextColor(primaryColor)
        binding.cmContactPropertyLabel.setTextColor(primaryColor)
        binding.cmContactClientLabel.setTextColor(primaryColor)

        val textColor = getProperTextColor()
        binding.cmContactNoteChevron.applyColorFilter(textColor)
        binding.cmContactPropertyChevron.applyColorFilter(textColor)
        binding.cmContactClientChevron.applyColorFilter(textColor)
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

    private fun populateNotes(notes: List<String>) {
        val list = binding.cmContactNoteList
        list.removeAllViews()
        val inflater = LayoutInflater.from(this)
        notes.forEach { note ->
            val row = ItemCmNoteBinding.inflate(inflater, list, false)
            row.cmNoteText.text = note
            list.addView(row.root)
        }
    }

    private fun populateRelated(list: LinearLayout, refs: List<CmRef>, path: String) {
        list.removeAllViews()
        if (refs.isEmpty()) {
            return
        }

        val inflater = LayoutInflater.from(this)
        val primaryColor = getProperPrimaryColor()
        val appUrl = config.cmAppUrl.trimEnd('/')

        refs.forEach { ref ->
            val row = ItemCmRelatedBinding.inflate(inflater, list, false)
            row.cmRelatedLabel.text = ref.label
            row.cmRelatedOpen.applyColorFilter(primaryColor)

            val open = { launchViewIntent("$appUrl$path/${ref.id}") }
            row.root.setOnClickListener { open() }
            row.cmRelatedOpen.setOnClickListener { open() }
            list.addView(row.root)
        }
    }

    private fun setupSection(
        holder: View,
        header: View,
        label: TextView,
        @StringRes titleRes: Int,
        count: Int,
        section: Section,
    ) {
        holder.beGoneIf(count == 0)
        if (count == 0) {
            return
        }
        label.text = getString(R.string.cm_section_title, getString(titleRes), count)
        header.setOnClickListener { toggleSection(section) }
    }

    private fun toggleSection(section: Section) {
        expandedSection = if (expandedSection == section) null else section
        applyExpansion(animate = true)
    }

    private fun applyExpansion(animate: Boolean) {
        setSectionExpanded(binding.cmContactNoteList, binding.cmContactNoteChevron, expandedSection == Section.NOTES, animate)
        setSectionExpanded(binding.cmContactPropertyList, binding.cmContactPropertyChevron, expandedSection == Section.PROPERTIES, animate)
        setSectionExpanded(binding.cmContactClientList, binding.cmContactClientChevron, expandedSection == Section.CLIENTS, animate)
    }

    private fun setSectionExpanded(list: View, chevron: View, expanded: Boolean, animate: Boolean) {
        list.beVisibleIf(expanded)
        val targetRotation = if (expanded) CHEVRON_EXPANDED_ROTATION else 0f
        if (animate) {
            chevron.animate().rotation(targetRotation).setDuration(CHEVRON_ROTATION_MS).start()
        } else {
            chevron.rotation = targetRotation
        }
    }

    companion object {
        private const val CHEVRON_EXPANDED_ROTATION = 180f
        private const val CHEVRON_ROTATION_MS = 150L
    }
}
