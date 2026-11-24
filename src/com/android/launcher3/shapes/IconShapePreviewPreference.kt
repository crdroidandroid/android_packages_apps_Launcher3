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
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R
import com.android.launcher3.graphics.ShapeDelegate
import com.android.launcher3.graphics.ShapeDelegate.Companion.pickBestShape
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.GraphicsUtils
import com.android.launcher3.icons.IconShape
import com.android.launcher3.settings.preferences.PreviewAppIcons
import com.android.launcher3.settings.preferences.PreviewWallpaper
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.SafeCloseable
import kotlin.math.roundToInt

/**
 * Live preview of the shape being edited, over a snippet of the wallpaper: a row of real app
 * icons, as the launcher renders them with the current icon pack and theme, above a row of
 * folders. Both rows show how the home screen would look with the edited [target] shape applied,
 * or, until a shape is set with [setPathString], with the shapes currently in use.
 */
class IconShapePreviewPreference
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs) {

    private val themeManager = ThemeManager.INSTANCE.get(context)
    private val iconBitmapSize = InvariantDeviceProfile.INSTANCE.get(context).iconBitmapSize

    /** What the edited shape applies to. */
    var target = ShapeTarget.ICON
        set(value) {
            if (field == value) return
            field = value
            folderShape = null
            updateIcons()
        }

    /** Path of the edited shape, or null to preview the shapes in use. */
    private var pathString: String? = null
    /** Shape generated for [pathString], created lazily since it renders a shadow bitmap. */
    private var iconShape: IconShape? = null
    private var folderShape: ShapeDelegate? = null

    private var loadStarted = false
    private var appIcons: List<BitmapInfo> = emptyList()
    private var wallpaper: Drawable? = null
    private var iconViews: List<ImageView> = emptyList()
    private var shapeChangeListener: SafeCloseable? = null

    init {
        layoutResource = R.layout.preference_icon_shape_preview
        isSelectable = false
        isPersistent = false
    }

    fun setPathString(path: String) {
        if (path == pathString) return
        pathString = path
        iconShape = null
        folderShape = null
        // Update the bound views directly; a full rebind on every slider step would flicker.
        updateIcons()
    }

    override fun onAttached() {
        super.onAttached()
        // Applying a shape updates the icon shape data, which is also dispatched for folders.
        shapeChangeListener =
            themeManager.iconShapeData.forEach(MAIN_EXECUTOR) {
                folderShape = null
                updateIcons()
            }
    }

    override fun onDetached() {
        shapeChangeListener?.close()
        shapeChangeListener = null
        super.onDetached()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
        loadPreviewContent()

        (holder.findViewById(R.id.icon_shape_preview_wallpaper) as ImageView)
            .setImageDrawable(wallpaper)

        val grid = holder.findViewById(R.id.icon_shape_preview_grid) as LinearLayout
        if (grid.childCount == 0) buildGrid(grid)
        iconViews = (0 until grid.childCount).flatMap { row ->
            val rowView = grid.getChildAt(row) as LinearLayout
            (0 until rowView.childCount).map { rowView.getChildAt(it) as ImageView }
        }
        updateIcons()
    }

    private fun buildGrid(grid: LinearLayout) {
        val density = context.resources.displayMetrics.density
        val iconSize = (ICON_SIZE_DP * density).roundToInt()
        val rowPadding = (ROW_PADDING_DP * density).roundToInt()
        repeat(ROWS) {
            val row =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(0, rowPadding, 0, rowPadding)
                }
            repeat(COLUMNS) {
                row.addView(
                    ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER },
                    LinearLayout.LayoutParams(0, iconSize, 1f),
                )
            }
            grid.addView(row)
        }
    }

    private fun updateIcons() {
        if (iconViews.isEmpty() || appIcons.isEmpty()) return
        val path = pathString
        val appShape =
            if (path != null && target == ShapeTarget.ICON)
                iconShape ?: createIconShape(path).also { iconShape = it }
            else themeManager.iconShapeData.value
        val folderShape = folderShape ?: previewFolderShape().also { folderShape = it }
        val folderColor = folderBackgroundColor()
        iconViews.forEachIndexed { i, view ->
            if (i < COLUMNS) {
                view.setImageDrawable(appIcons.getOrNull(i)?.let { newIcon(it, appShape) })
            } else {
                val first = COLUMNS + (i - COLUMNS) * FOLDER_ITEMS
                val items = appIcons.drop(first).take(FOLDER_ITEMS).map { newIcon(it, appShape) }
                view.setImageDrawable(
                    FolderPreviewDrawable(folderShape, items, folderColor, iconBitmapSize)
                )
            }
        }
    }

    /**
     * Returns the folder shape the edited shape would produce: the shape itself when editing
     * folders, or when folders match the (custom) icon shape; otherwise the folder shape in use.
     */
    private fun previewFolderShape(): ShapeDelegate {
        val path = pathString ?: return themeManager.folderShape
        val followsIcon = ShapeTarget.FOLDER.shapePref.get(context).isEmpty()
        return if (target == ShapeTarget.FOLDER || followsIcon) pickBestShape(path)
        else themeManager.folderShape
    }

    private fun newIcon(info: BitmapInfo, shape: IconShape): Drawable =
        PreviewAppIcons.newIcon(context, info, shape)

    private fun folderBackgroundColor(): Int =
        context.getColor(
            if (context.resources.configuration.isNightModeActive) R.color.folder_preview_dark
            else R.color.folder_preview_light
        )

    private fun createIconShape(path: String): IconShape =
        GraphicsUtils.generateIconShape(
            iconBitmapSize,
            pickBestShape(shapeStr = path).getPath(iconBitmapSize.toFloat()),
        )

    private fun loadPreviewContent() {
        if (loadStarted) return
        loadStarted = true
        val appContext = context.applicationContext
        MODEL_EXECUTOR.execute {
            val icons = PreviewAppIcons.load(appContext, MAX_APPS)
            val wallpaper = PreviewWallpaper.load(appContext)
            MAIN_EXECUTOR.execute {
                appIcons = icons
                this.wallpaper = wallpaper
                notifyChanged()
            }
        }
    }

    private companion object {
        const val ROWS = 2
        const val COLUMNS = 4
        const val ICON_SIZE_DP = 56
        const val ROW_PADDING_DP = 8
        const val FOLDER_ITEMS = 4
        /** Enough apps for a row of icons and a row of filled folders. */
        const val MAX_APPS = COLUMNS + COLUMNS * FOLDER_ITEMS
    }
}
