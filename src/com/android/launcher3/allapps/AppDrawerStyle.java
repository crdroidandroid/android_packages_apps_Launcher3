/*
 * Copyright (C) 2026 VoltageOS
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

package com.android.launcher3.allapps;

import android.content.Context;
import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.core.graphics.ColorUtils;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.util.Themes;

public final class AppDrawerStyle {

    public static final String NORMAL = "normal";
    public static final String HORIZONTAL_LIST = "horizontal_list";
    public static final String VERTICAL_PAGED = "vertical";
    public static final String FULLSCREEN = "fullscreen";

    private AppDrawerStyle() { }

    public static String get(Context context) {
        String style = LauncherPrefs.APP_DRAWER_STYLE.get(context);
        return isSupported(style) ? style : NORMAL;
    }

    public static boolean isSupported(String style) {
        return NORMAL.equals(style)
                || HORIZONTAL_LIST.equals(style)
                || VERTICAL_PAGED.equals(style)
                || FULLSCREEN.equals(style);
    }

    public static boolean isNormal(String style) {
        return NORMAL.equals(style);
    }

    public static boolean isHorizontalList(String style) {
        return HORIZONTAL_LIST.equals(style);
    }

    public static boolean isVerticalPaged(String style) {
        return VERTICAL_PAGED.equals(style);
    }

    public static boolean isFullscreen(String style) {
        return FULLSCREEN.equals(style) || VERTICAL_PAGED.equals(style);
    }

    public static boolean isFullscreen(Context context) {
        return isFullscreen(get(context));
    }

    /**
     * Returns the themed surface used behind all apps.
     *
     * <p>All drawer layouts represent the same surface. Keeping one token prevents normal,
     * fullscreen and paged styles from changing tone when blur availability changes.
     */
    public static int getThemedBackgroundColor(Context context) {
        return context.getColor(R.color.materialColorSurfaceContainerLow);
    }

    /** Returns the opaque drawer color before the user-selected opacity is applied. */
    @ColorInt
    public static int getBackgroundColor(Context context) {
        if (LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_ENABLED.get(context)) {
            int color = com.android.launcher3.Utilities.isDarkTheme(context)
                    ? LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_DARK.get(context)
                    : LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_LIGHT.get(context);
            return ColorUtils.setAlphaComponent(color, 255);
        }
        return getThemedBackgroundColor(context);
    }

    /** Returns a legible primary content color for themed and custom drawer backgrounds. */
    @ColorInt
    public static int getContentColor(Context context) {
        if (LauncherPrefs.ALL_APPS_DARK_TEXT.get(context)) {
            return context.getColor(R.color.all_apps_label_color_dark_forced);
        }
        if (LauncherPrefs.APP_DRAWER_OPACITY.get(context) <= 30) {
            return Themes.getAttrColor(context, R.attr.workspaceTextColor);
        }
        if (!LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_ENABLED.get(context)) {
            return Themes.getAttrColor(context, android.R.attr.textColorPrimary);
        }
        return getContrastingContentColor(context, getBackgroundColor(context));
    }

    @ColorInt
    private static int getContrastingContentColor(Context context, @ColorInt int background) {
        int dark = context.getColor(R.color.all_apps_label_color_dark_forced);
        int light = context.getColor(R.color.text_color_primary_dark);
        return ColorUtils.calculateContrast(dark, background)
                >= ColorUtils.calculateContrast(light, background) ? dark : light;
    }

    /** Returns a search surface that follows custom backgrounds instead of the system night mode. */
    @ColorInt
    public static int getSearchBackgroundColor(Context context) {
        if (!LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_ENABLED.get(context)) {
            return context.getColor(R.color.materialColorSurfaceBright);
        }
        int background = getBackgroundColor(context);
        float whiteBlend = ColorUtils.calculateLuminance(background) >= 0.5 ? 0.35f : 0.16f;
        return ColorUtils.blendARGB(background, Color.WHITE, whiteBlend);
    }

    /** Returns content with guaranteed contrast against the search/fast-scroll surface. */
    @ColorInt
    public static int getSearchContentColor(Context context) {
        if (LauncherPrefs.ALL_APPS_DARK_TEXT.get(context)) {
            return context.getColor(R.color.all_apps_label_color_dark_forced);
        }
        return getContrastingContentColor(context, getSearchBackgroundColor(context));
    }
}
