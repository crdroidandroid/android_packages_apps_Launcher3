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
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.children
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.SelectorWithWidgetPreference

/** Radio preference showing an icon shape preview at the end of the row. */
class IconShapePreference(context: Context, pathString: String) :
    SelectorWithWidgetPreference(context) {

    private val preview = IconShapePreviewDrawable(context, pathString)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        // Reuse the extra widget slot as a non-interactive preview.
        val image = extraWidget as? ImageView ?: return
        image.apply {
            setImageDrawable(preview)
            imageTintList = null
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = null
            setOnClickListener(null)
            isClickable = false
            isFocusable = false
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        (image.parent as? ViewGroup)?.apply {
            visibility = View.VISIBLE
            isClickable = false
            // Hide the two-target divider, the preview is part of the row.
            children.filter { it !== image }.forEach { it.visibility = View.GONE }
        }
    }
}
