package org.fossify.phone.dialogs

import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.value
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.DialogCmUrlBinding

/**
 * Small single-field editor for a CM URL setting (base URL / app URL). The
 * trimmed value is handed back via [callback]; an empty value is allowed
 * (it means "use the built-in FakeCmApi" for the base URL).
 */
class CmUrlDialog(
    private val activity: SimpleActivity,
    @StringRes titleRes: Int,
    @StringRes hintRes: Int,
    currentValue: String,
    private val callback: (String) -> Unit,
) {
    private val binding = DialogCmUrlBinding.inflate(activity.layoutInflater)

    init {
        binding.cmUrlHint.setText(hintRes)
        binding.cmUrlValue.setText(currentValue)

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, titleRes) { alertDialog ->
                    alertDialog.showKeyboard(binding.cmUrlValue)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        callback(binding.cmUrlValue.value.trim())
                        alertDialog.dismiss()
                    }
                }
            }
    }
}
