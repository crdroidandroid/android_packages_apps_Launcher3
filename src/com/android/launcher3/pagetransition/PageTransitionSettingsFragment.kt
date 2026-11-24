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

package com.android.launcher3.pagetransition

import android.content.Context
import android.os.Bundle
import androidx.preference.ListPreference
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.PageTransitionEffect
import com.android.launcher3.R
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/** Previews the selected page transition above a list to pick the transition from. */
class PageTransitionSettingsFragment : SettingsBasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val current = currentValue(context)
        val screen = preferenceManager.createPreferenceScreen(context)
        setPreferenceScreen(screen)

        val preview =
            PageTransitionPreviewPreference(context).apply {
                key = KEY_PREVIEW
                effect = PageTransitionEffect.fromValue(current)
            }
        screen.addPreference(preview)

        screen.addPreference(
            ListPreference(context).apply {
                key = KEY_EFFECT
                setTitle(R.string.page_transition_title)
                setDialogTitle(R.string.page_transition_title)
                setEntries(R.array.page_transition_entries)
                setEntryValues(R.array.page_transition_values)
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                isIconSpaceReserved = false
                // Stored through LauncherPrefs, which owns the preference file.
                isPersistent = false
                value = current
                setOnPreferenceChangeListener { _, newValue ->
                    val value = newValue as String
                    LauncherPrefs.get(context).put(LauncherPrefs.PAGE_TRANSITION, value)
                    preview.effect = PageTransitionEffect.fromValue(value)
                    true
                }
            }
        )
    }

    companion object {
        private const val KEY_PREVIEW = "page_transition_preview"
        private const val KEY_EFFECT = "page_transition_effect"

        /** Returns the stored value, falling back to none when it is unknown. */
        private fun currentValue(context: Context): String =
            PageTransitionEffect.fromValue(LauncherPrefs.PAGE_TRANSITION.get(context)).value

        /** Returns the title of the page transition in use, for display as a summary. */
        @JvmStatic
        fun getCurrentLabel(context: Context): String {
            val values = context.resources.getStringArray(R.array.page_transition_values)
            val entries = context.resources.getStringArray(R.array.page_transition_entries)
            return entries.getOrElse(values.indexOf(currentValue(context))) { entries[0] }
        }
    }
}
