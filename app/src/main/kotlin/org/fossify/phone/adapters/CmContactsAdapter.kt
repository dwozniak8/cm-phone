package org.fossify.phone.adapters

import android.annotation.SuppressLint
import android.text.SpannableString
import android.view.Menu
import android.view.ViewGroup
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.highlightTextPart
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.views.MyRecyclerView
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.cm.CmContact
import org.fossify.phone.databinding.ItemCmContactBinding

/**
 * Renders the local CM contact cache in the CM tab. Read-only list: no
 * multi-select / CAB, tap opens the CM contact detail (Batch 3).
 */
class CmContactsAdapter(
    activity: SimpleActivity,
    var contacts: List<CmContact>,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit,
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    private var textToHighlight = ""
    private val contactsHelper = SimpleContactsHelper(activity)

    override fun getActionMenuId() = 0

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = 0

    override fun getIsItemSelectable(position: Int) = false

    override fun getItemSelectionKey(position: Int): Int? = null

    override fun getItemKeyPosition(key: Int) = -1

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(ItemCmContactBinding.inflate(layoutInflater, parent, false).root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bindView(contact, allowSingleClick = true, allowLongClick = false) { itemView, _ ->
            setupView(ItemCmContactBinding.bind(itemView), contact)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = contacts.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: List<CmContact>, highlightText: String = "") {
        textToHighlight = highlightText
        contacts = newItems
        notifyDataSetChanged()
    }

    private fun setupView(binding: ItemCmContactBinding, contact: CmContact) {
        val displayName = contact.fullName.ifBlank { contact.primaryNumber }

        binding.cmContactName.apply {
            text = if (textToHighlight.isEmpty()) {
                displayName
            } else {
                SpannableString(displayName.highlightTextPart(textToHighlight, properPrimaryColor))
            }
            setTextColor(textColor)
        }

        binding.cmContactNumber.apply {
            text = contact.primaryNumber
            setTextColor(textColor)
        }

        contactsHelper.loadContactImage(contact.photoUrl.orEmpty(), binding.cmContactImage, displayName)
    }
}
