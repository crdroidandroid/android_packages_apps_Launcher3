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
import com.android.launcher3.graphics.ThemeManager
import com.android.settingslib.widget.SelectorWithWidgetPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/**
 * Lists the custom shape and the preset shapes for a [ShapeTarget], each with a preview, and
 * applies the selected one.
 */
class IconShapeSettingsFragment : SettingsBasePreferenceFragment() {

    private val target by lazy {
        ShapeTarget.fromName(arguments?.getString(IconShapeSettingsActivity.EXTRA_TARGET))
    }
    private val radios = mutableListOf<SelectorWithWidgetPreference>()
    private var shownCustomShape: String? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        // Returning from the editor may have created, changed or applied the custom shape.
        val prefs = LauncherPrefs.get(requireContext())
        if (target.customShape(prefs)?.serialize() != shownCustomShape) {
            buildScreen()
        } else {
            updateChecked(currentShapeKey(requireContext(), target))
        }
    }

    private fun buildScreen() {
        val context = preferenceManager.context
        val customShape = target.customShape(LauncherPrefs.get(context))
        val currentKey = currentShapeKey(context, target)
        shownCustomShape = customShape?.serialize()
        radios.clear()

        val screen = preferenceManager.createPreferenceScreen(context)
        setPreferenceScreen(screen)

        // Previews the shapes in use, updating as soon as another one is selected.
        screen.addPreference(
            IconShapePreviewPreference(context).apply {
                key = KEY_PREVIEW
                target = this@IconShapeSettingsFragment.target
            }
        )

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
                    setTitle(target.customCreate)
                    setIcon(R.drawable.ic_plus)
                } else {
                    setTitle(target.customEdit)
                    setIcon(R.drawable.gm_edit_24)
                }
                setOnPreferenceClickListener {
                    startActivity(IconShapeSettingsActivity.createEditorIntent(context, target))
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
                context.getString(target.defaultLabel),
                defaultPreviewPath(context),
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

    /** Path previewed for the default entry; empty previews the system icon mask. */
    private fun defaultPreviewPath(context: Context): String {
        if (target == ShapeTarget.ICON) return ""
        // Folders follow a custom icon shape exactly, and other shapes as a rounded square.
        if (ShapeTarget.ICON.shapePref.get(context) == CustomIconShape.KEY) {
            ShapeTarget.ICON.customShape(LauncherPrefs.get(context))?.let { return it.pathString }
        }
        val radius = ThemeManager.INSTANCE.get(context).iconState.folderRadius
        val corner = ShapeCorner(CornerStyle.ROUND, radius)
        return ShapePathBuilder.fromCorners(corner, corner, corner, corner)
    }

    private fun select(key: String) {
        val value = if (key == KEY_SYSTEM_DEFAULT) "" else key
        LauncherPrefs.get(requireContext()).put(target.shapePref, value)
        updateChecked(key)
    }

    private fun updateChecked(currentKey: String) {
        radios.forEach { it.isChecked = it.key == currentKey }
    }

    companion object {
        /**
         * Preference key for the default entry, stored as an empty value: the system mask for
         * icons, the icon shape for folders.
         */
        private const val KEY_SYSTEM_DEFAULT = "system_default"
        private const val KEY_PREVIEW = "shape_preview"
        private const val KEY_CATEGORY_CUSTOM = "icon_shape_category_custom"
        private const val KEY_CATEGORY_PRESETS = "icon_shape_category_presets"
        private const val KEY_EDIT_CUSTOM = "icon_shape_edit_custom"

        /** Returns the key of the radio matching the shape in use. */
        private fun currentShapeKey(context: Context, target: ShapeTarget): String =
            when (val stored = target.shapePref.get(context)) {
                CustomIconShape.KEY ->
                    if (target.customShape(LauncherPrefs.get(context)) != null) stored
                    else KEY_SYSTEM_DEFAULT
                else ->
                    ShapesProvider.iconShapes.firstOrNull { it.key == stored }?.key
                        ?: KEY_SYSTEM_DEFAULT
            }

        /** Returns the title of the [target] shape in use, for display as a summary. */
        @JvmStatic
        @JvmOverloads
        fun getCurrentShapeLabel(context: Context, target: ShapeTarget = ShapeTarget.ICON): String {
            val key = currentShapeKey(context, target)
            return when (key) {
                KEY_SYSTEM_DEFAULT -> context.getString(target.defaultLabel)
                CustomIconShape.KEY -> context.getString(R.string.icon_shape_custom)
                else ->
                    ShapesProvider.iconShapes.firstOrNull { it.key == key }
                        ?.let { context.getString(it.titleId) }
                        ?: context.getString(target.defaultLabel)
            }
        }
    }
}
