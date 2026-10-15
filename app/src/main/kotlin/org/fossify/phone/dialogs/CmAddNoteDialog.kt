package org.fossify.phone.dialogs

import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.cm.CmApiException
import org.fossify.phone.cm.CmClient
import org.fossify.phone.cm.CmContact
import org.fossify.phone.cm.CmContactStore
import org.fossify.phone.cm.CmContactsUpdatedEvent
import org.fossify.phone.cm.CmErrorCodes
import org.fossify.phone.cm.NoteTarget
import org.fossify.phone.cm.NoteTargetType
import org.fossify.phone.databinding.DialogCmAddNoteBinding
import org.fossify.phone.databinding.ItemCmNoteTargetBinding
import org.greenrobot.eventbus.EventBus

/**
 * "Dopisz notatkę" - append a note to an existing CM contact, or to one of its
 * related build / client records via the "Notatka dotyczy" picker. Updates the
 * local cache optimistically from the server's response.
 */
class CmAddNoteDialog(
    private val activity: SimpleActivity,
    private val cmContact: CmContact,
    private val onSaved: (() -> Unit)? = null,
) {
    private val binding = DialogCmAddNoteBinding.inflate(activity.layoutInflater)
    private var dialog: AlertDialog? = null
    private var targetPickerDialog: AlertDialog? = null

    private data class TargetOption(val label: String, val target: NoteTarget?)

    private val options: List<TargetOption> = buildList {
        add(TargetOption(activity.getString(R.string.cm_note_target_contact, contactName()), null))
        cmContact.relatedProperties.forEach {
            add(TargetOption(it.label, NoteTarget(NoteTargetType.BUILD, it.id)))
        }
        cmContact.relatedClients.forEach {
            add(TargetOption(it.label, NoteTarget(NoteTargetType.CLIENT, it.id)))
        }
    }
    private var selectedIndex = 0

    init {
        binding.cmAddNoteName.text = contactName()

        binding.cmAddNoteTargetHolder.beVisible()
        binding.cmAddNoteTarget.setOnClickListener { pickTarget() }
        renderSelectedTarget()

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.cm_add_note) { alertDialog ->
                    dialog = alertDialog
                    alertDialog.showKeyboard(binding.cmAddNoteText)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save() }
                }
            }
    }

    private fun contactName() = cmContact.fullName.ifBlank { cmContact.primaryNumber }

    private fun iconFor(target: NoteTarget?): Int = when (target?.type) {
        NoteTargetType.BUILD -> R.drawable.ic_home_vector
        NoteTargetType.CLIENT -> R.drawable.ic_briefcase_vector
        else -> R.drawable.ic_person_small_vector
    }

    private fun renderSelectedTarget() {
        val option = options[selectedIndex]
        binding.cmAddNoteTarget.text = option.label

        val icon = ContextCompat.getDrawable(activity, iconFor(option.target))?.mutate()
        icon?.setTint(activity.getProperTextColor())
        binding.cmAddNoteTarget.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
        binding.cmAddNoteTarget.compoundDrawablePadding =
            activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.normal_margin)
    }

    private fun pickTarget() {
        val textColor = activity.getProperTextColor()
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        options.forEachIndexed { index, option ->
            val row = ItemCmNoteTargetBinding.inflate(activity.layoutInflater, list, false)
            row.cmNoteTargetIcon.setImageResource(iconFor(option.target))
            row.cmNoteTargetIcon.applyColorFilter(textColor)
            row.cmNoteTargetLabel.text = option.label
            row.cmNoteTargetLabel.setTextColor(textColor)
            row.root.setOnClickListener {
                selectedIndex = index
                renderSelectedTarget()
                targetPickerDialog?.dismiss()
            }
            list.addView(row.root)
        }

        val scroll = ScrollView(activity).apply { addView(list) }
        activity.getAlertDialogBuilder()
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(scroll, this, R.string.cm_note_target) { alertDialog ->
                    targetPickerDialog = alertDialog
                }
            }
    }

    private fun save() {
        val note = binding.cmAddNoteText.value
        if (note.isEmpty()) {
            activity.toast(R.string.cm_note_hint)
            return
        }

        val target = options[selectedIndex].target
        dialog?.dismiss()
        ensureBackgroundThread {
            try {
                val updated = CmClient.getApi(activity).addNote(cmContact.id, note, target)
                CmContactStore.upsert(activity, updated)
                EventBus.getDefault().post(CmContactsUpdatedEvent())
                activity.runOnUiThread {
                    activity.toast(R.string.cm_note_added)
                    onSaved?.invoke()
                }
            } catch (e: CmApiException) {
                val messageRes = if (e.errorCode == CmErrorCodes.ITEM_NOT_FOUND || e.statusCode == HTTP_NOT_FOUND) {
                    R.string.cm_contact_not_found
                } else {
                    R.string.cm_save_failed
                }
                activity.runOnUiThread { activity.toast(messageRes) }
            }
        }
    }

    companion object {
        private const val HTTP_NOT_FOUND = 404
    }
}
