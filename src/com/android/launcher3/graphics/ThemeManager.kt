/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.graphics

import android.content.Context
import android.content.res.Resources
import androidx.annotation.AnyThread
import com.android.launcher3.ConstantItem
import com.android.launcher3.LauncherPrefChangeListener
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.backedUpItem
import com.android.launcher3.R
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.display.OverlayChangeHandler
import com.android.launcher3.graphics.ShapeDelegate.Companion.DEFAULT_PATH_SIZE_INT
import com.android.launcher3.graphics.ShapeDelegate.Companion.pickBestShape
import com.android.launcher3.graphics.theme.IconThemeFactory
import com.android.launcher3.graphics.theme.ThemePreference
import com.android.launcher3.graphics.theme.ThemePreference.Companion.MONO_THEME_VALUE
import com.android.launcher3.icons.DotRenderer.IconShapeInfo
import com.android.launcher3.icons.GraphicsUtils.generateIconShape
import com.android.launcher3.icons.IconShape
import com.android.launcher3.icons.IconThemeController
import com.android.launcher3.shapes.CustomIconShape
import com.android.launcher3.shapes.IconShapeModel
import com.android.launcher3.shapes.IconShapeModel.Companion.DEFAULT_ICON_RADIUS
import com.android.launcher3.shapes.ShapesProvider
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.ListenableRef
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.SafeCloseable
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Named

/** Centralized class for managing Launcher icon theming */
@LauncherAppSingleton
class ThemeManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val prefs: LauncherPrefs,
    private val themePreference: ThemePreference,
    @Named(ICON_FACTORY_DAGGER_KEY)
    private val iconThemeFactories: Map<String, @JvmSuppressWildcards IconThemeFactory>,
    @Ui mainExecutor: LooperExecutor,
    overlayChangeHandler: OverlayChangeHandler,
    lifecycle: DaggerSingletonTracker,
) {

    private val _iconShapeData = MutableListenableRef(IconShape.EMPTY)

    /** listenable value holder for current IconShape */
    val iconShapeData: ListenableRef<IconShape> = _iconShapeData.asListenable()
    /** Representation of the current icon state */
    var iconState = parseIconState(null)
        private set

    @Deprecated("Use [ThemePreference] instead")
    var isMonoThemeEnabled
        set(value) = themePreference.setValue(if (value) MONO_THEME_VALUE else null)
        get() = MONO_THEME_VALUE == themePreference.value

    val themeController
        get() = iconState.themeController

    val isIconThemeEnabled
        get() = themeController != null

    val iconShape
        get() = iconState.iconShape

    val folderShape
        get() = iconState.folderShape

    val fileShape
        get() = iconState.fileShape

    var fileShapeData = fileShape.createIconShape(DEFAULT_PATH_SIZE_INT)
        private set

    private val listeners = CopyOnWriteArrayList<ThemeChangeListener>()

    init {
        lifecycle.addCloseable(overlayChangeHandler.addCallback { verifyIconState() })

        val prefListener = LauncherPrefChangeListener {
            if (SHAPE_PREFS.any { pref -> pref.sharedPrefKey == it }) verifyIconState()
        }
        prefs.addListener(prefListener, *SHAPE_PREFS)
        lifecycle.addCloseable(themePreference.forEach(mainExecutor) { verifyIconState() })
        lifecycle.addCloseable {
            prefs.removeListener(prefListener, *SHAPE_PREFS)
            iconState.closeController()
        }
    }

    private fun verifyIconState() {
        val newState = parseIconState(iconState)
        val oldState = iconState
        if (newState == oldState) return
        val hasThemedChanged =
            newState.themeCode != oldState.themeCode ||
                newState.isCircle != oldState.isCircle ||
                newState.accentColor != oldState.accentColor
        iconState = newState
        if (hasThemedChanged) {
            // trigger listeners only for theme change, not shape change
            listeners.forEach { it.onThemeChanged() }
        }
        // Shape changes rebind the model, which also redraws folders with a new folder shape.
        if (
            newState.iconShape != oldState.iconShape ||
                newState.folderShape != oldState.folderShape
        ) {
            _iconShapeData.dispatchValue(iconShape.createIconShape(iconShapeData.value.pathSize))
        }
    }

    @AnyThread fun addChangeListener(listener: ThemeChangeListener) = listeners.add(listener)

    @AnyThread fun removeChangeListener(listener: ThemeChangeListener) = listeners.remove(listener)

    /**
     * Generates new IconShape based given [iconSize] and current [iconShape] Allocates new Bitmap
     * via [createIconShape]
     */
    fun generateIconShape(iconSize: Int) {
        if (iconShapeData.value.pathSize != iconSize) {
            _iconShapeData.dispatchValue(iconShape.createIconShape(iconSize))
        }
        if (fileShapeData.pathSize != iconSize) {
            fileShapeData = fileShape.createIconShape(iconSize)
        }
    }

    private fun parseIconState(oldState: IconState?): IconState {
        val shapeModel = resolveShapeModel(PREF_ICON_SHAPE, PREF_CUSTOM_ICON_SHAPE)
        val iconMask =
            when {
                shapeModel != null -> shapeModel.pathString
                CONFIG_ICON_MASK_RES_ID == Resources.ID_NULL -> ""
                else -> context.resources.getString(CONFIG_ICON_MASK_RES_ID)
            }

        val iconShape =
            if (oldState != null && oldState.iconMask == iconMask) {
                oldState.iconShape
            } else {
                pickBestShape(iconMask)
            }

        val folderRadius = shapeModel?.folderRadiusRatio ?: 1f
        val folderMask =
            resolveShapeModel(PREF_FOLDER_SHAPE, PREF_CUSTOM_FOLDER_SHAPE)?.pathString
                ?: shapeModel?.takeIf { it.key == CustomIconShape.KEY }?.pathString
                ?: ""
        val folderShape =
            if (
                oldState != null &&
                    oldState.folderMask == folderMask &&
                    (folderMask.isNotEmpty() || oldState.folderRadius == folderRadius)
            ) {
                oldState.folderShape
            } else if (folderMask.isNotEmpty()) {
                pickBestShape(folderMask)
            } else if (folderRadius == 1f) {
                ShapeDelegate.Circle()
            } else {
                ShapeDelegate.RoundedSquare(folderRadius)
            }

        val fileShape =
            oldState?.fileShape
                ?: run {
                    val res = context.resources
                    val path = res.getString(R.string.home_screen_files_file_icon_path_data)
                    ShapeDelegate.GenericPathShape(path)
                }

        val themeKey = themePreference.value
        val themeCode = themeKey?.toString() ?: "no-theme"

        val iconControllerFactory =
            if (oldState?.themeCode == themeCode) {
                oldState.themeController
            } else {
                oldState?.closeController()
                themeKey?.run { iconThemeFactories[factoryId]?.createController(themeId) }
            }

        return IconState(
            iconMask = iconMask,
            folderRadius = folderRadius,
            folderMask = folderMask,
            themeController = iconControllerFactory,
            iconShape = iconShape,
            folderShape = folderShape,
            shapeRadius = shapeModel?.shapeRadius ?: DEFAULT_ICON_RADIUS,
            themeCode = themeCode,
            fileShape = fileShape,
            accentColor = context.resources.getColor(android.R.color.system_accent1_600, context.theme),
        )
    }

    private fun resolveShapeModel(
        shapePref: ConstantItem<String>,
        customShapePref: ConstantItem<String>,
    ): IconShapeModel? =
        when (val key = prefs.get(shapePref)) {
            CustomIconShape.KEY -> CustomIconShape.parse(prefs.get(customShapePref))?.toModel()
            else -> ShapesProvider.iconShapes.firstOrNull { it.key == key }
        }

    data class IconState(
        val iconMask: String,
        val folderRadius: Float,
        val folderMask: String,
        val themeController: IconThemeController?,
        val themeCode: String,
        val iconShape: ShapeDelegate,
        /* Icon content may change when using Circle shape due to android:roundIcon property */
        val isCircle: Boolean = iconShape is ShapeDelegate.Circle,
        val folderShape: ShapeDelegate,
        val shapeRadius: Float,
        val fileShape: ShapeDelegate,
        val accentColor: Int,
    ) {
        val iconShapeInfo = IconShapeInfo.fromPath(iconShape.getPath(), DEFAULT_PATH_SIZE_INT)
    }

    private fun IconState.closeController() {
        if (themeController is SafeCloseable) {
            themeController.close()
        }
    }

    /** Interface for receiving theme change events */
    fun interface ThemeChangeListener {
        fun onThemeChanged()
    }

    companion object {

        @JvmField val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getThemeManager)
        @JvmField val PREF_ICON_SHAPE = backedUpItem("icon_shape_model", "")
        @JvmField val PREF_CUSTOM_ICON_SHAPE = backedUpItem("icon_shape_custom", "")
        @JvmField val PREF_FOLDER_SHAPE = backedUpItem("folder_shape_model", "")
        @JvmField val PREF_CUSTOM_FOLDER_SHAPE = backedUpItem("folder_shape_custom", "")
        @JvmField val PREF_RESHAPE_PACK_ICONS = backedUpItem("icon_shape_reshape_pack_icons", false)

        private val SHAPE_PREFS =
            arrayOf(
                PREF_ICON_SHAPE,
                PREF_CUSTOM_ICON_SHAPE,
                PREF_FOLDER_SHAPE,
                PREF_CUSTOM_FOLDER_SHAPE,
            )

        @JvmField val DEFAULT_SHAPE_DELEGATE = pickBestShape(shapeStr = "")

        private val CONFIG_ICON_MASK_RES_ID: Int =
            Resources.getSystem().getIdentifier("config_icon_mask", "string", "android")

        private fun ShapeDelegate.createIconShape(size: Int) =
            generateIconShape(size, getPath(size.toFloat()))

        const val ICON_FACTORY_DAGGER_KEY = "ICON_FACTORIES"
    }
}
