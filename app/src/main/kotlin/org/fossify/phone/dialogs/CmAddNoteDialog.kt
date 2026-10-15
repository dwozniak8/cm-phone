package org.fossify.phone.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
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
import org.fossify.phone.databinding.DialogCmAddNoteBinding
import org.greenrobot.eventbus.EventBus

/**
 * "Dopisz notatkę" - append a note to an existing CM contact. Updates the local
 * cache optimistically from the server's response.
 */
class CmAddNoteDialog(
    private val activity: SimpleActivity,
    private val cmContact: CmContact,
    private val onSaved: (() -> Unit)? = null,
) {
    private val binding = DialogCmAddNoteBinding.inflate(activity.layoutInflater)
    private var dialog: AlertDialog? = null

    init {
        binding.cmAddNoteName.text = cmContact.fullName.ifBlank { cmContact.primaryNumber }

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

    private fun save() {
        val note = binding.cmAddNoteText.value
        if (note.isEmpty()) {
            activity.toast(R.string.cm_note_hint)
            return
        }

        dialog?.dismiss()
        ensureBackgroundThread {
            try {
                val updated = CmClient.getApi(activity).addNote(cmContact.id, note)
                CmContactStore.upsert(activity, updated)
                EventBus.getDefault().post(CmContactsUpdatedEvent())
                activity.runOnUiThread {
                    activity.toast(R.string.cm_note_added)
                    onSaved?.invoke()
                }
            } catch (e: CmApiException) {
                activity.runOnUiThread { activity.toast(R.string.cm_save_failed) }
            }
        }
    }
}
