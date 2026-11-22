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
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.graphics.ThemeManager.Companion.PREF_ICON_SHAPE
import com.android.launcher3.icons.pack.RadioSettingsFragment
import com.android.settingslib.widget.SelectorWithWidgetPreference

/** Lists the available icon shapes, each with a preview, and applies the selected one. */
class IconShapeSettingsFragment : RadioSettingsFragment() {

    override fun getPreferences(context: Context): List<SelectorWithWidgetPreference> {
        val currentKey = currentShape(context)?.key ?: KEY_SYSTEM_DEFAULT
        val systemDefault =
            buildPreference(
                context,
                KEY_SYSTEM_DEFAULT,
                context.getString(R.string.icon_shape_default_label),
                "",
                currentKey,
            )
        return listOf(systemDefault) +
            ShapesProvider.iconShapes.map {
                buildPreference(
                    context,
                    it.key,
                    context.getString(it.titleId),
                    it.pathString,
                    currentKey,
                )
            }
    }

    private fun buildPreference(
        context: Context,
        key: String,
        title: String,
        pathString: String,
        currentKey: String,
    ): SelectorWithWidgetPreference =
        IconShapePreference(context, pathString).apply {
            this.key = key
            this.title = title
            isPersistent = false
            isChecked = key == currentKey
            if (isChecked) setSelectedPreference(this)
        }

    public override fun onSelected(key: String?) {
        val value = if (key == null || key == KEY_SYSTEM_DEFAULT) "" else key
        LauncherPrefs.get(requireContext()).put(PREF_ICON_SHAPE, value)
        super.onSelected(key)
    }

    companion object {
        /** Preference key for the system default shape, stored as an empty value. */
        private const val KEY_SYSTEM_DEFAULT = "system_default"

        /** Returns the shape in use, or null when the system default applies. */
        private fun currentShape(context: Context): IconShapeModel? {
            val stored = PREF_ICON_SHAPE.get(context)
            if (stored.isEmpty()) return null
            return ShapesProvider.iconShapes.firstOrNull { it.key == stored }
        }

        /** Returns the title of the shape in use, for display as a summary. */
        @JvmStatic
        fun getCurrentShapeLabel(context: Context): String =
            context.getString(currentShape(context)?.titleId ?: R.string.icon_shape_default_label)
    }
}
