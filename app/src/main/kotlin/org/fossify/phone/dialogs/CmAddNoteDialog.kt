package org.fossify.phone.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.RadioItem
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
import org.greenrobot.eventbus.EventBus

/**
 * "Dopisz notatkę" - append a note to an existing CM contact, or to one of its
 * related build / client records when the target dropdown is used. Updates the
 * local cache optimistically from the server's response.
 */
class CmAddNoteDialog(
    private val activity: SimpleActivity,
    private val cmContact: CmContact,
    private val onSaved: (() -> Unit)? = null,
) {
    private val binding = DialogCmAddNoteBinding.inflate(activity.layoutInflater)
    private var dialog: AlertDialog? = null

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
        binding.cmAddNoteTarget.text = options[selectedIndex].label
        binding.cmAddNoteTarget.setOnClickListener { pickTarget() }

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

    private fun pickTarget() {
        val items = options.mapIndexed { index, option -> RadioItem(index, option.label) }
        RadioGroupDialog(activity, ArrayList(items), selectedIndex) { selected ->
            selectedIndex = selected as Int
            binding.cmAddNoteTarget.text = options[selectedIndex].label
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
