package org.fossify.phone.cm

import android.widget.ImageView
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getContrastColor
import org.fossify.phone.R

/**
 * CM contacts don't get a per-person photo - instead every CM avatar is the same
 * glyph ([R.drawable.ic_cm_contact_vector]) on a coloured circle, so a "contact
 * from the app" is instantly recognisable and not mistaken for a phone contact.
 * The circle colour varies per contact (derived from a stable key) just to keep
 * a long list visually distinct.
 */
object CmAvatar {

    private val backgroundColors = intArrayOf(
        0xFF1E88E5.toInt(), // blue
        0xFF3949AB.toInt(), // indigo
        0xFF00897B.toInt(), // teal
        0xFF43A047.toInt(), // green
        0xFF8E24AA.toInt(), // purple
        0xFFF4511E.toInt(), // deep orange
        0xFF6D4C41.toInt(), // brown
    )

    fun backgroundColorFor(key: String): Int {
        val index = (key.hashCode() % backgroundColors.size + backgroundColors.size) % backgroundColors.size
        return backgroundColors[index]
    }

    /** Renders the CM glyph into [imageView]. Padding is left to the layout. */
    fun apply(imageView: ImageView, key: String) {
        val background = backgroundColorFor(key)
        imageView.apply {
            setImageResource(R.drawable.ic_cm_contact_vector)
            setBackgroundResource(org.fossify.commons.R.drawable.circle_background)
            this.background.applyColorFilter(background)
            setColorFilter(background.getContrastColor())
        }
    }
}
