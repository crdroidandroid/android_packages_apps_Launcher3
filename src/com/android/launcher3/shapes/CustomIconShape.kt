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

import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.graphics.ThemeManager.Companion.PREF_CUSTOM_ICON_SHAPE
import com.android.launcher3.shapes.IconShapeModel.Companion.DEFAULT_ICON_RADIUS

/** User defined icon shape, built from four individually styled corners. */
data class CustomIconShape(
    val topLeft: ShapeCorner,
    val topRight: ShapeCorner,
    val bottomLeft: ShapeCorner,
    val bottomRight: ShapeCorner,
) {

    val pathString: String by lazy {
        ShapePathBuilder.fromCorners(topLeft, topRight, bottomRight, bottomLeft)
    }

    /** Serialized form: `v1|<style>,<size>|...` in top-left, top-right, bottom-left, bottom-right order. */
    fun serialize(): String =
        listOf(topLeft, topRight, bottomLeft, bottomRight).joinToString(
            separator = SEPARATOR,
            prefix = VERSION + SEPARATOR,
        ) {
            "${it.style.key}$CORNER_SEPARATOR${it.size}"
        }

    /** Returns the shape as an [IconShapeModel] which the launcher can apply. */
    fun toModel(): IconShapeModel {
        val averageSize =
            (topLeft.size + topRight.size + bottomLeft.size + bottomRight.size) / 4f
        return IconShapeModel(
            key = KEY,
            titleId = R.string.icon_shape_custom,
            pathString = pathString,
            folderRadiusRatio = averageSize,
            shapeRadius = DEFAULT_ICON_RADIUS * averageSize,
        )
    }

    companion object {
        /** Value of the icon shape preference selecting the custom shape. */
        const val KEY = "custom"

        private const val VERSION = "v1"
        private const val SEPARATOR = "|"
        private const val CORNER_SEPARATOR = ","

        @JvmField
        val DEFAULT =
            ShapeCorner(CornerStyle.ROUND, 0.5f).let { CustomIconShape(it, it, it, it) }

        /** Parses a value produced by [serialize], returning null when it is invalid. */
        @JvmStatic
        fun parse(value: String?): CustomIconShape? {
            val parts = value?.split(SEPARATOR) ?: return null
            if (parts.size != 5 || parts[0] != VERSION) return null
            val corners = parts.drop(1).map { parseCorner(it) ?: return null }
            return CustomIconShape(corners[0], corners[1], corners[2], corners[3])
        }

        private fun parseCorner(value: String): ShapeCorner? {
            val parts = value.split(CORNER_SEPARATOR)
            if (parts.size != 2) return null
            val style = CornerStyle.fromKey(parts[0]) ?: return null
            val size = parts[1].toFloatOrNull()?.takeIf { it in 0f..1f } ?: return null
            return ShapeCorner(style, size)
        }

        /** Returns the saved custom shape, or null when none was created. */
        @JvmStatic
        fun fromPrefs(prefs: LauncherPrefs): CustomIconShape? = parse(prefs.get(PREF_CUSTOM_ICON_SHAPE))
    }
}
