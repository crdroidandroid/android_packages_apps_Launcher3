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

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.graphics.ThemeManager.Companion.PREF_CUSTOM_ICON_SHAPE
import com.android.launcher3.graphics.ThemeManager.Companion.PREF_ICON_SHAPE
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/** Edits the four corners of the custom icon shape, previewing the result live. */
class IconShapeEditorFragment : SettingsBasePreferenceFragment() {

    /** Links a corner's preference to the matching field of [CustomIconShape]. */
    private class CornerBinding(
        val key: String,
        val get: (CustomIconShape) -> ShapeCorner,
        val set: (CustomIconShape, ShapeCorner) -> CustomIconShape,
    )

    private val corners =
        listOf(
            CornerBinding("custom_shape_top_left", { it.topLeft }, { s, c -> s.copy(topLeft = c) }),
            CornerBinding("custom_shape_top_right", { it.topRight }, { s, c -> s.copy(topRight = c) }),
            CornerBinding(
                "custom_shape_bottom_left",
                { it.bottomLeft },
                { s, c -> s.copy(bottomLeft = c) },
            ),
            CornerBinding(
                "custom_shape_bottom_right",
                { it.bottomRight },
                { s, c -> s.copy(bottomRight = c) },
            ),
        )

    /** The saved custom shape, null when none was created yet. */
    private var saved: CustomIconShape? = null
    private lateinit var draft: CustomIconShape
    private var preview: IconShapePreviewPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = LauncherFiles.SHARED_PREFERENCES_KEY
        setPreferencesFromResource(R.xml.launcher_icon_shape_editor, rootKey)

        saved = CustomIconShape.fromPrefs(LauncherPrefs.get(requireContext()))
        draft =
            CustomIconShape.parse(savedInstanceState?.getString(STATE_DRAFT))
                ?: saved
                ?: CustomIconShape.DEFAULT

        preview = findPreference(KEY_PREVIEW)
        corners.forEach { bindCorner(it) }
        updatePreview()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity()
            .addMenuProvider(
                object : MenuProvider {
                    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                        menuInflater.inflate(R.menu.menu_icon_shape_editor, menu)
                    }

                    override fun onPrepareMenu(menu: Menu) {
                        menu.findItem(R.id.menu_icon_shape_apply)?.isEnabled = hasChanges()
                    }

                    override fun onMenuItemSelected(item: MenuItem): Boolean {
                        if (item.itemId != R.id.menu_icon_shape_apply) return false
                        applyDraft()
                        return true
                    }
                },
                viewLifecycleOwner,
                Lifecycle.State.RESUMED,
            )
    }

    /** Whether the draft differs from the saved custom shape, or the initial one if none. */
    private fun hasChanges() = draft != (saved ?: CustomIconShape.DEFAULT)

    private fun bindCorner(binding: CornerBinding) {
        findPreference<ShapeCornerPreference>(binding.key)?.apply {
            setCorner(binding.get(draft))
            onCornerChangeListener = { corner -> updateDraft(binding, corner) }
        }
    }

    private fun updateDraft(binding: CornerBinding, corner: ShapeCorner) {
        draft = binding.set(draft, corner)
        updatePreview()
        activity?.invalidateOptionsMenu()
    }

    private fun updatePreview() {
        preview?.setPathString(draft.pathString)
    }

    private fun applyDraft() {
        LauncherPrefs.get(requireContext())
            .put(PREF_CUSTOM_ICON_SHAPE to draft.serialize(), PREF_ICON_SHAPE to CustomIconShape.KEY)
        activity?.finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_DRAFT, draft.serialize())
    }

    private companion object {
        const val KEY_PREVIEW = "custom_shape_preview"
        const val STATE_DRAFT = "custom_shape_draft"
    }
}
