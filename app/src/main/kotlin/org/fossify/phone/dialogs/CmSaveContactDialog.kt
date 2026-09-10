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
import org.fossify.phone.cm.CmContactStore
import org.fossify.phone.cm.CmContactsUpdatedEvent
import org.fossify.phone.cm.CmErrorCodes
import org.fossify.phone.cm.CmPhoneNumber
import org.fossify.phone.cm.CreateContactRequest
import org.fossify.phone.databinding.DialogCmSaveContactBinding
import org.greenrobot.eventbus.EventBus

/**
 * "Zapisz w aplikacji" - create a CM contact from a call-log entry. The phone
 * number is fixed; first name is required, last name and note optional. On
 * success the local cache is updated optimistically.
 */
class CmSaveContactDialog(
    private val activity: SimpleActivity,
    private val phoneNumber: String,
    private val prefillName: String = "",
    private val focusNote: Boolean = false,
    private val onSaved: (() -> Unit)? = null,
) {
    private val binding = DialogCmSaveContactBinding.inflate(activity.layoutInflater)
    private var dialog: AlertDialog? = null

    init {
        binding.cmSaveContactNumber.text = phoneNumber
        if (prefillName.isNotBlank() && prefillName != phoneNumber) {
            val parts = prefillName.trim().split(" ", limit = 2)
            binding.cmSaveContactFirstName.setText(parts.getOrElse(0) { "" })
            binding.cmSaveContactLastName.setText(parts.getOrElse(1) { "" })
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.cm_save_in_app) { alertDialog ->
                    dialog = alertDialog
                    val toFocus = if (focusNote) binding.cmSaveContactNote else binding.cmSaveContactFirstName
                    alertDialog.showKeyboard(toFocus)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save() }
                }
            }
    }

    private fun save() {
        val firstName = binding.cmSaveContactFirstName.value
        val lastName = binding.cmSaveContactLastName.value
        val note = binding.cmSaveContactNote.value

        if (firstName.isEmpty()) {
            activity.toast(R.string.cm_first_name_required)
            return
        }

        dialog?.dismiss()
        ensureBackgroundThread {
            try {
                val created = CmClient.getApi(activity).createContact(
                    CreateContactRequest(
                        firstName = firstName,
                        lastName = lastName,
                        note = note.ifBlank { null },
                        phoneNumbers = listOf(CmPhoneNumber(phoneNumber)),
                    )
                )
                CmContactStore.upsert(activity, created)
                EventBus.getDefault().post(CmContactsUpdatedEvent())
                activity.runOnUiThread {
                    activity.toast(R.string.cm_saved)
                    onSaved?.invoke()
                }
            } catch (e: CmApiException) {
                val messageRes = when (e.errorCode) {
                    CmErrorCodes.CONTACT_ALREADY_EXISTS -> R.string.cm_contact_already_exists
                    CmErrorCodes.INVALID_PHONE_NUMBER -> R.string.cm_invalid_phone_number
                    else -> R.string.cm_save_failed
                }
                activity.runOnUiThread { activity.toast(messageRes) }
            }
        }
    }
}
