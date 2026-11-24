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

package com.android.launcher3.settings.preferences

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.Log
import androidx.annotation.WorkerThread
import com.android.launcher3.Flags
import com.android.launcher3.LauncherAppState
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.BitmapInfo.Companion.FLAG_NO_BADGE
import com.android.launcher3.icons.BitmapInfo.Companion.FLAG_THEMED
import com.android.launcher3.icons.IconShape
import com.android.launcher3.icons.cache.CacheLookupFlag.Companion.DEFAULT_LOOKUP_FLAG
import com.android.launcher3.model.data.AppInfo

object PreviewAppIcons {

    private const val TAG = "PreviewAppIcons"

    @JvmStatic
    @WorkerThread
    fun load(context: Context, max: Int): List<BitmapInfo> =
        runCatching { loadIcons(context, max) }
            .onFailure { Log.w(TAG, "Unable to load preview icons", it) }
            .getOrDefault(emptyList())

    @JvmStatic
    @JvmOverloads
    fun newIcon(
        context: Context,
        info: BitmapInfo,
        shape: IconShape = ThemeManager.INSTANCE.get(context).iconShapeData.value,
    ): Drawable {
        val themeManager = ThemeManager.INSTANCE.get(context)
        val flags = FLAG_NO_BADGE or if (themeManager.isIconThemeEnabled) FLAG_THEMED else 0
        val customShape =
            if (Flags.enableLauncherIconShapes() && info.isFullBleed()) shape else null
        return info.newIcon(context, flags, customShape)
    }

    private fun loadIcons(context: Context, max: Int): List<BitmapInfo> {
        val user = Process.myUserHandle()
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val iconCache = LauncherAppState.getInstance(context).iconCache
        val lookupFlag =
            DEFAULT_LOOKUP_FLAG.withUseLowRes(false)
                .withThemeIcon(ThemeManager.INSTANCE.get(context).isIconThemeEnabled)
        return launcherApps
            .getActivityList(null, user)
            .distinctBy { it.componentName.packageName }
            .sortedBy { it.label.toString().lowercase() }
            .take(max)
            .map { activity ->
                AppInfo(context, activity, user)
                    .also { iconCache.getTitleAndIcon(it, activity, lookupFlag) }
                    .bitmap
            }
    }
}
