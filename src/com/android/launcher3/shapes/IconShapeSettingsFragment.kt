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
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.graphics.ThemeManager.Companion.PREF_ICON_SHAPE
import com.android.settingslib.widget.SelectorWithWidgetPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/**
 * Lists the custom shape and the preset shapes, each with a preview, and applies the selected
 * one.
 */
class IconShapeSettingsFragment : SettingsBasePreferenceFragment() {

    private val radios = mutableListOf<SelectorWithWidgetPreference>()
    private var shownCustomShape: String? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        // Returning from the editor may have created, changed or applied the custom shape.
        val prefs = LauncherPrefs.get(requireContext())
        if (CustomIconShape.fromPrefs(prefs)?.serialize() != shownCustomShape) {
            buildScreen()
        } else {
            updateChecked(currentShapeKey(requireContext()))
        }
    }

    private fun buildScreen() {
        val context = preferenceManager.context
        val customShape = CustomIconShape.fromPrefs(LauncherPrefs.get(context))
        val currentKey = currentShapeKey(context)
        shownCustomShape = customShape?.serialize()
        radios.clear()

        val screen = preferenceManager.createPreferenceScreen(context)
        setPreferenceScreen(screen)

        val customCategory =
            PreferenceCategory(context).apply {
                key = KEY_CATEGORY_CUSTOM
                setTitle(R.string.icon_shape_custom)
            }
        screen.addPreference(customCategory)
        customShape?.let {
            customCategory.addPreference(
                buildRadio(
                    context,
                    CustomIconShape.KEY,
                    context.getString(R.string.icon_shape_custom),
                    it.pathString,
                )
            )
        }
        customCategory.addPreference(
            Preference(context).apply {
                key = KEY_EDIT_CUSTOM
                isPersistent = false
                if (customShape == null) {
                    setTitle(R.string.icon_shape_custom_create)
                    setIcon(R.drawable.ic_plus)
                } else {
                    setTitle(R.string.icon_shape_custom_edit)
                    setIcon(R.drawable.gm_edit_24)
                }
                setOnPreferenceClickListener {
                    startActivity(IconShapeSettingsActivity.createEditorIntent(context))
                    true
                }
            }
        )

        val presetsCategory =
            PreferenceCategory(context).apply {
                key = KEY_CATEGORY_PRESETS
                setTitle(R.string.icon_shape_presets)
            }
        screen.addPreference(presetsCategory)
        presetsCategory.addPreference(
            buildRadio(
                context,
                KEY_SYSTEM_DEFAULT,
                context.getString(R.string.icon_shape_default_label),
                "",
            )
        )
        ShapesProvider.iconShapes.forEach {
            presetsCategory.addPreference(
                buildRadio(context, it.key, context.getString(it.titleId), it.pathString)
            )
        }

        updateChecked(currentKey)
    }

    private fun buildRadio(
        context: Context,
        key: String,
        title: String,
        pathString: String,
    ): SelectorWithWidgetPreference =
        IconShapePreference(context, pathString).apply {
            this.key = key
            this.title = title
            isPersistent = false
            setOnPreferenceClickListener {
                select(it.key)
                true
            }
            radios.add(this)
        }

    private fun select(key: String) {
        val value = if (key == KEY_SYSTEM_DEFAULT) "" else key
        LauncherPrefs.get(requireContext()).put(PREF_ICON_SHAPE, value)
        updateChecked(key)
    }

    private fun updateChecked(currentKey: String) {
        radios.forEach { it.isChecked = it.key == currentKey }
    }

    companion object {
        /** Preference key for the system default shape, stored as an empty value. */
        private const val KEY_SYSTEM_DEFAULT = "system_default"
        private const val KEY_CATEGORY_CUSTOM = "icon_shape_category_custom"
        private const val KEY_CATEGORY_PRESETS = "icon_shape_category_presets"
        private const val KEY_EDIT_CUSTOM = "icon_shape_edit_custom"

        /** Returns the key of the radio matching the shape in use. */
        private fun currentShapeKey(context: Context): String =
            when (val stored = PREF_ICON_SHAPE.get(context)) {
                CustomIconShape.KEY ->
                    if (CustomIconShape.fromPrefs(LauncherPrefs.get(context)) != null) stored
                    else KEY_SYSTEM_DEFAULT
                else ->
                    ShapesProvider.iconShapes.firstOrNull { it.key == stored }?.key
                        ?: KEY_SYSTEM_DEFAULT
            }

        /** Returns the title of the shape in use, for display as a summary. */
        @JvmStatic
        fun getCurrentShapeLabel(context: Context): String {
            val key = currentShapeKey(context)
            return when (key) {
                KEY_SYSTEM_DEFAULT -> context.getString(R.string.icon_shape_default_label)
                CustomIconShape.KEY -> context.getString(R.string.icon_shape_custom)
                else ->
                    ShapesProvider.iconShapes.firstOrNull { it.key == key }
                        ?.let { context.getString(it.titleId) }
                        ?: context.getString(R.string.icon_shape_default_label)
            }
        }
    }
}
