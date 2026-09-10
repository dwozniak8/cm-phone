package org.fossify.phone.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.launchViewIntent
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.R
import org.fossify.phone.cm.CmAvatar
import org.fossify.phone.cm.CmContact
import org.fossify.phone.cm.CmContactStore
import org.fossify.phone.databinding.ActivityCmContactBinding
import org.fossify.phone.databinding.ItemCmNoteBinding
import org.fossify.phone.databinding.ItemCmPhoneNumberBinding
import org.fossify.phone.databinding.ItemCmRecordBinding
import org.fossify.phone.dialogs.CmAddNoteDialog
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.startCallWithConfirmationCheck
import org.fossify.phone.helpers.CM_CLIENT_PATH
import org.fossify.phone.helpers.CM_CONTACT_ID
import org.fossify.phone.helpers.CM_PROPERTY_PATH

/**
 * Read-only detail view for a single CM contact. Everything comes from the local
 * [CmContactStore] cache, so it works offline.
 *
 * The body is a single collapsible list of records - the contact first, then its
 * related properties / clients, each with a type icon on the left. Expanding a
 * row reveals that record's notes; opening one collapses the others. Property /
 * client rows also carry an "open in CM" deep-link button. The toolbar overflow
 * mirrors the call-history contact menu ("Dopisz notatkę" / "Zadzwoń").
 */
class CmContactActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityCmContactBinding::inflate)

    private var contact: CmContact? = null
    private var expandedRecordIndex: Int? = null
    private val recordChevrons = mutableListOf<View>()
    private val recordNoteContainers = mutableListOf<View>()

    private data class RecordRow(
        val label: String,
        val iconRes: Int,
        val notes: List<String>,
        val deepLink: String?,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomSystem = listOf(binding.cmContactScrollview))
        setupMaterialScrollListener(binding.cmContactScrollview, binding.cmContactAppbar)

        binding.cmContactToolbar.apply {
            menu.findItem(R.id.cm_action_save).isVisible = false
            menu.findItem(R.id.cm_action_open_details).isVisible = false
            setOnMenuItemClickListener { onMenuItemClick(it) }
        }

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

    private fun onMenuItemClick(item: MenuItem): Boolean {
        val current = contact ?: return false
        return when (item.itemId) {
            R.id.cm_action_add_note -> {
                CmAddNoteDialog(this, current) { reloadContact() }
                true
            }

            R.id.cm_action_call -> {
                startCallWithConfirmationCheck(current.primaryNumber, displayName(current))
                true
            }

            else -> false
        }
    }

    private fun reloadContact() {
        val id = contact?.id ?: return
        CmContactStore.findById(id)?.let {
            contact = it
            bindContact(it)
        }
    }

    private fun displayName(contact: CmContact) = contact.fullName.ifBlank { contact.primaryNumber }

    private fun bindContact(contact: CmContact) {
        val name = displayName(contact)
        binding.cmContactToolbar.title = name
        binding.cmContactName.text = name
        CmAvatar.apply(binding.cmContactPhoto, contact.id)

        buildPhoneNumberRows(contact, name)
        renderRecords(contact, name)

        updateTextColors(binding.cmContactScrollview)
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

    private fun renderRecords(contact: CmContact, displayName: String) {
        val holder = binding.cmContactRecords
        holder.removeAllViews()
        recordChevrons.clear()
        recordNoteContainers.clear()

        val inflater = LayoutInflater.from(this)
        val textColor = getProperTextColor()
        val primaryColor = getProperPrimaryColor()
        val appUrl = config.cmAppUrl.trimEnd('/')

        val rows = buildList {
            add(RecordRow(displayName, R.drawable.ic_person_small_vector, contact.notes, null))
            contact.relatedProperties.forEach {
                add(RecordRow(it.label, R.drawable.ic_home_vector, it.notes, "$appUrl$CM_PROPERTY_PATH/${it.id}"))
            }
            contact.relatedClients.forEach {
                add(RecordRow(it.label, R.drawable.ic_briefcase_vector, it.notes, "$appUrl$CM_CLIENT_PATH/${it.id}"))
            }
        }

        rows.forEachIndexed { index, row ->
            val view = ItemCmRecordBinding.inflate(inflater, holder, false)
            view.cmRecordIcon.setImageResource(row.iconRes)
            view.cmRecordIcon.applyColorFilter(textColor)
            view.cmRecordLabel.text = row.label
            view.cmRecordLabel.setTextColor(textColor)
            view.cmRecordChevron.applyColorFilter(textColor)

            view.cmRecordOpen.beVisibleIf(row.deepLink != null)
            row.deepLink?.let { link ->
                view.cmRecordOpen.applyColorFilter(primaryColor)
                view.cmRecordOpen.setOnClickListener { launchViewIntent(link) }
            }

            fillNotes(inflater, view.cmRecordNotes, row.notes, textColor)

            view.cmRecordHeader.setOnClickListener { toggleRecord(index) }
            recordChevrons.add(view.cmRecordChevron)
            recordNoteContainers.add(view.cmRecordNotes)
            holder.addView(view.root)
        }

        expandedRecordIndex = if (rows.isEmpty()) null else 0
        applyRecordExpansion(animate = false)
    }

    private fun fillNotes(inflater: LayoutInflater, container: ViewGroup, notes: List<String>, textColor: Int) {
        if (notes.isEmpty()) {
            val row = ItemCmNoteBinding.inflate(inflater, container, false)
            row.cmNoteBullet.beGone()
            row.cmNoteText.apply {
                text = getString(R.string.cm_record_no_notes)
                setTextColor(textColor)
                alpha = EMPTY_NOTE_ALPHA
            }
            container.addView(row.root)
            return
        }

        notes.forEach { note ->
            val row = ItemCmNoteBinding.inflate(inflater, container, false)
            row.cmNoteText.text = note
            row.cmNoteText.setTextColor(textColor)
            container.addView(row.root)
        }
    }

    private fun toggleRecord(index: Int) {
        expandedRecordIndex = if (expandedRecordIndex == index) null else index
        applyRecordExpansion(animate = true)
    }

    private fun applyRecordExpansion(animate: Boolean) {
        recordNoteContainers.forEachIndexed { index, notes ->
            val expanded = expandedRecordIndex == index
            notes.beVisibleIf(expanded)

            val targetRotation = if (expanded) CHEVRON_EXPANDED_ROTATION else 0f
            val chevron = recordChevrons[index]
            if (animate) {
                chevron.animate().rotation(targetRotation).setDuration(CHEVRON_ROTATION_MS).start()
            } else {
                chevron.rotation = targetRotation
            }
        }
    }

    companion object {
        private const val CHEVRON_EXPANDED_ROTATION = 180f
        private const val CHEVRON_ROTATION_MS = 150L
        private const val EMPTY_NOTE_ALPHA = 0.6f
    }
}
