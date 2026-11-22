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
import android.widget.ImageView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.launcher3.R

/** Large, non-interactive preview of the icon shape being edited. */
class IconShapePreviewPreference
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs) {

    private var pathString = ""

    init {
        layoutResource = R.layout.preference_icon_shape_preview
        isSelectable = false
        isPersistent = false
    }

    fun setPathString(path: String) {
        if (path == pathString) return
        pathString = path
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
        (holder.findViewById(R.id.icon_shape_preview) as? ImageView)?.setImageDrawable(
            IconShapePreviewDrawable(context, pathString, PREVIEW_SIZE_DP)
        )
    }

    private companion object {
        const val PREVIEW_SIZE_DP = 128
    }
}
