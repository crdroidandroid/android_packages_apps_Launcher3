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
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.launcher3.PageTransitionEffect
import com.android.launcher3.R
import com.android.launcher3.settings.preferences.PreviewAppIcons
import com.android.launcher3.settings.preferences.PreviewWallpaper
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import kotlin.math.roundToInt

/** Shows [PageTransitionPreviewView] playing the selected [effect] with the user's apps. */
class PageTransitionPreviewPreference
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs) {

    var effect = PageTransitionEffect.NONE
        set(value) {
            field = value
            // Update the bound view directly, a rebind would restart the preview.
            previewView?.effect = value
        }

    private var loadStarted = false
    private var icons: List<Drawable> = emptyList()
    private var wallpaper: Drawable? = null
    private var previewView: PageTransitionPreviewView? = null

    init {
        layoutResource = R.layout.preference_page_transition_preview
        isSelectable = false
        isPersistent = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
        loadPreviewContent()

        previewView =
            (holder.findViewById(R.id.page_transition_preview) as PageTransitionPreviewView)
                .also {
                    // Make the preview fill most of the screen, leaving the picker below it.
                    val height =
                        (context.resources.displayMetrics.heightPixels * SCREEN_HEIGHT_FRACTION)
                            .roundToInt()
                    if (it.layoutParams.height != height) {
                        it.layoutParams = it.layoutParams.apply { this.height = height }
                    }
                    it.effect = effect
                    it.icons = icons
                    it.wallpaper = wallpaper
                }
    }

    private fun loadPreviewContent() {
        if (loadStarted) return
        loadStarted = true
        val appContext = context.applicationContext
        MODEL_EXECUTOR.execute {
            val infos = PreviewAppIcons.load(appContext, PageTransitionPreviewView.ICON_COUNT)
            val wallpaper = PreviewWallpaper.load(appContext)
            MAIN_EXECUTOR.execute {
                val icons = infos.map { PreviewAppIcons.newIcon(appContext, it) }
                this.icons = icons
                this.wallpaper = wallpaper
                previewView?.let {
                    it.icons = icons
                    it.wallpaper = wallpaper
                }
            }
        }
    }

    private companion object {
        const val SCREEN_HEIGHT_FRACTION = 0.62f
    }
}
