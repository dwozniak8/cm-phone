package org.fossify.phone.dialogs

import android.content.Intent
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.PopupMenu
import org.fossify.commons.extensions.getPopupMenuTheme
import org.fossify.phone.R
import org.fossify.phone.activities.CmContactActivity
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.cm.CmContactStore
import org.fossify.phone.helpers.CM_CONTACT_ID

/**
 * The compact CM actions popup shared by the call-history rows and the dialer's
 * CM search results. Options depend on whether [phoneNumber] is already a CM
 * contact: a new number only offers "Zapisz w aplikacji" / "Zadzwoń", an existing
 * one also "Dopisz notatkę" / "Otwórz szczegóły".
 */
object CmContactActionsMenu {

    fun show(
        activity: SimpleActivity,
        anchor: View,
        phoneNumber: String,
        displayName: String,
        onCall: () -> Unit,
    ) {
        CmContactStore.ensureLoaded(activity)
        val cmContact = CmContactStore.findByNumber(phoneNumber)

        val contextTheme = ContextThemeWrapper(activity, activity.getPopupMenuTheme())
        PopupMenu(contextTheme, anchor, Gravity.START).apply {
            inflate(R.menu.menu_recent_cm_options)
            menu.findItem(R.id.cm_action_save).isVisible = cmContact == null
            menu.findItem(R.id.cm_action_add_note).isVisible = cmContact != null
            menu.findItem(R.id.cm_action_open_details).isVisible = cmContact != null

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.cm_action_save ->
                        CmSaveContactDialog(activity, phoneNumber, prefillName = displayName)

                    R.id.cm_action_add_note ->
                        cmContact?.let { CmAddNoteDialog(activity, it) }

                    R.id.cm_action_open_details ->
                        cmContact?.let {
                            activity.startActivity(
                                Intent(activity, CmContactActivity::class.java).putExtra(CM_CONTACT_ID, it.id)
                            )
                        }

                    R.id.cm_action_call -> onCall()
                }
                true
            }
            show()
        }
    }
}
