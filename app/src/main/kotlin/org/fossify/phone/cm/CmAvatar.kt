package org.fossify.phone.cm

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.widget.ImageView
import androidx.core.content.ContextCompat
import org.fossify.commons.extensions.getContrastColor
import org.fossify.phone.R

/**
 * CM contacts don't get a per-person photo - instead every CM avatar is the same
 * glyph ([R.drawable.ic_cm_contact_vector]) on a coloured circle, so a "contact
 * from the app" is instantly recognisable and not mistaken for a phone contact.
 * The circle colour varies per contact (derived from a stable key) just to keep
 * a long list visually distinct.
 *
 * The circle + glyph are composed into a single `src` drawable (not a background)
 * so the avatar fills exactly the same box as a normal letter/photo avatar -
 * `scaleType` and `padding` on the ImageView apply to both the same way.
 */
object CmAvatar {

    private const val GLYPH_INSET_DP = 4f

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

    fun apply(imageView: ImageView, key: String) {
        val bgColor = backgroundColorFor(key)

        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(bgColor)
        }
        val glyph = ContextCompat.getDrawable(imageView.context, R.drawable.ic_cm_contact_vector)!!
            .mutate()
        glyph.setTint(bgColor.getContrastColor())

        val layered = LayerDrawable(arrayOf(circle, glyph))
        val inset = (imageView.resources.displayMetrics.density * GLYPH_INSET_DP).toInt()
        layered.setLayerInset(1, inset, inset, inset, inset)

        imageView.background = null
        imageView.clearColorFilter()
        imageView.setImageDrawable(layered)
    }
}
