package org.fossify.phone.dialogs

import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity

/**
 * Shown before a manually dialled call when the typed number already belongs to
 * a CM contact - the client is probably already being handled by someone.
 * [onProceed] runs only if the user chooses to call anyway.
 */
class CmNumberInUseDialog(
    activity: SimpleActivity,
    contactName: String,
    onProceed: () -> Unit,
) {
    init {
        ConfirmationDialog(
            activity = activity,
            message = activity.getString(R.string.cm_number_in_use, contactName),
            positive = R.string.cm_call_anyway,
            negative = org.fossify.commons.R.string.cancel,
        ) {
            onProceed()
        }
    }
}
