/*
 * Copyright (C) 2026 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.shapes

import android.content.Context
import android.util.AttributeSet
import android.view.Menu
import android.widget.PopupMenu
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.launcher3.R
import com.google.android.material.slider.Slider
import java.text.NumberFormat
import kotlin.math.roundToInt

/** Edits one corner of a custom icon shape: its size on a slider and its style from a menu. */
class ShapeCornerPreference
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs) {

    /** Called whenever the user changes the size or style of the corner. */
    var onCornerChangeListener: ((ShapeCorner) -> Unit)? = null

    var corner: ShapeCorner = CustomIconShape.DEFAULT.topLeft
        private set

    private val percentFormat = NumberFormat.getPercentInstance()

    init {
        layoutResource = R.layout.preference_shape_corner
        isSelectable = false
        isPersistent = false
    }

    fun setCorner(value: ShapeCorner) {
        if (value == corner) return
        corner = value
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false

        val sizeText = holder.findViewById(R.id.shape_corner_size) as TextView
        val styleText = holder.findViewById(R.id.shape_corner_style) as TextView
        val slider = holder.findViewById(R.id.shape_corner_slider) as Slider

        sizeText.text = percentFormat.format(corner.size)
        bindStyle(styleText)

        // The holder may be recycled from another corner, so drop its old listener first.
        slider.clearOnChangeListeners()
        slider.value = (corner.size * 100).roundToInt().toFloat()
        slider.contentDescription = title
        slider.setLabelFormatter { percentFormat.format(it / 100) }
        slider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            // Update in place: rebinding mid-drag would interrupt the gesture.
            corner = corner.copy(size = value / 100)
            sizeText.text = percentFormat.format(corner.size)
            onCornerChangeListener?.invoke(corner)
        }

        styleText.setOnClickListener { showStyleMenu(styleText) }
    }

    private fun bindStyle(styleText: TextView) {
        val label = context.getString(corner.style.labelRes)
        styleText.text = label
        styleText.contentDescription = "$title, $label"
    }

    private fun showStyleMenu(anchor: TextView) {
        PopupMenu(context, anchor).apply {
            CornerStyle.entries.forEach { style ->
                menu.add(Menu.NONE, style.ordinal, style.ordinal, style.labelRes).apply {
                    isCheckable = true
                    isChecked = style == corner.style
                }
            }
            menu.setGroupCheckable(Menu.NONE, true, true)
            setOnMenuItemClickListener { item ->
                val style = CornerStyle.entries[item.itemId]
                if (style != corner.style) {
                    corner = corner.copy(style = style)
                    bindStyle(anchor)
                    onCornerChangeListener?.invoke(corner)
                }
                true
            }
            show()
        }
    }

    private companion object {
        @get:StringRes
        val CornerStyle.labelRes: Int
            get() =
                when (this) {
                    CornerStyle.ROUND -> R.string.icon_shape_corner_round
                    CornerStyle.SMOOTH -> R.string.icon_shape_corner_smooth
                    CornerStyle.CUT -> R.string.icon_shape_corner_cut
                }
    }
}
