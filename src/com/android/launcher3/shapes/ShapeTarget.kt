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

import androidx.annotation.StringRes
import com.android.launcher3.ConstantItem
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.graphics.ThemeManager.Companion.PREF_CUSTOM_FOLDER_SHAPE
import com.android.launcher3.graphics.ThemeManager.Companion.PREF_CUSTOM_ICON_SHAPE
import com.android.launcher3.graphics.ThemeManager.Companion.PREF_FOLDER_SHAPE
import com.android.launcher3.graphics.ThemeManager.Companion.PREF_ICON_SHAPE

/** What a shape setting applies to: app icons or folders. */
enum class ShapeTarget(
    /** Selected shape key; empty selects [defaultLabel]. */
    val shapePref: ConstantItem<String>,
    /** Serialized [CustomIconShape]. */
    val customShapePref: ConstantItem<String>,
    @StringRes val title: Int,
    @StringRes val defaultLabel: Int,
    @StringRes val customTitle: Int,
    @StringRes val customCreate: Int,
    @StringRes val customEdit: Int,
) {
    ICON(
        PREF_ICON_SHAPE,
        PREF_CUSTOM_ICON_SHAPE,
        R.string.icon_shape_title,
        R.string.icon_shape_default_label,
        R.string.icon_shape_custom_title,
        R.string.icon_shape_custom_create,
        R.string.icon_shape_custom_edit,
    ),
    FOLDER(
        PREF_FOLDER_SHAPE,
        PREF_CUSTOM_FOLDER_SHAPE,
        R.string.folder_shape_title,
        R.string.folder_shape_match_icon,
        R.string.folder_shape_custom_title,
        R.string.folder_shape_custom_create,
        R.string.folder_shape_custom_edit,
    );

    /** Returns the saved custom shape, or null when none was created. */
    fun customShape(prefs: LauncherPrefs): CustomIconShape? =
        CustomIconShape.parse(prefs.get(customShapePref))

    companion object {
        @JvmStatic
        fun fromName(name: String?): ShapeTarget = entries.firstOrNull { it.name == name } ?: ICON
    }
}
