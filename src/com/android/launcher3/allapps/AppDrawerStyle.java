/*
 * Copyright (C) 2026 VoltageOS
 *           (C) 2026 crDroid Android Project
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
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.Themes;

/**
 * App drawer presentation styles and colors.
 * <ul>
 *   <li>{@link #NORMAL}: stock bottom sheet with a vertically scrolling A-Z grid.</li>
 *   <li>{@link #HORIZONTAL_LIST}: bottom sheet with one app per row (icon + label side by side).</li>
 *   <li>{@link #VERTICAL_PAGED}: fullscreen, horizontally paged grid (One UI style).</li>
 *   <li>{@link #FULLSCREEN}: fullscreen panel with a vertically scrolling A-Z grid.</li>
 * </ul>
 */
public final class AppDrawerStyle {

    public static final String NORMAL = "normal";
    public static final String HORIZONTAL_LIST = "horizontal_list";
    public static final String VERTICAL_PAGED = "vertical";
    public static final String FULLSCREEN = "fullscreen";

    /** Alpha used for hint text on top of the search surface (~70%). */
    public static final int HINT_ALPHA = 179;

    private AppDrawerStyle() { }

    /** Returns the user-selected style, falling back to {@link #NORMAL} for unknown values. */
    public static String get(Context context) {
        String style = LauncherPrefs.APP_DRAWER_STYLE.get(context);
        return isSupported(style) ? style : NORMAL;
    }

    public static boolean isSupported(@Nullable String style) {
        return NORMAL.equals(style)
                || HORIZONTAL_LIST.equals(style)
                || VERTICAL_PAGED.equals(style)
                || FULLSCREEN.equals(style);
    }

    public static boolean isNormal(@Nullable String style) {
        return style == null || NORMAL.equals(style);
    }

    public static boolean isHorizontalList(@Nullable String style) {
        return HORIZONTAL_LIST.equals(style);
    }

    public static boolean isVerticalPaged(@Nullable String style) {
        return VERTICAL_PAGED.equals(style);
    }

    /** Fullscreen styles have no rounded sheet; the paged style is always fullscreen. */
    public static boolean isFullscreen(@Nullable String style) {
        return FULLSCREEN.equals(style) || VERTICAL_PAGED.equals(style);
    }

    public static boolean isFullscreen(Context context) {
        return isFullscreen(get(context));
    }

    // ---------------------------------------------------------------------------------------------
    // Colors
    // ---------------------------------------------------------------------------------------------

    /** Whether the user picked a custom drawer background color. */
    public static boolean isCustomColorEnabled(Context context) {
        return LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_ENABLED.get(context);
    }

    /**
     * Returns the opaque custom background color for the current (light/dark) theme. Only
     * meaningful when {@link #isCustomColorEnabled(Context)} is true.
     */
    @ColorInt
    public static int getCustomBackgroundColor(Context context) {
        int color = Utilities.isDarkTheme(context)
                ? LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_DARK.get(context)
                : LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_LIGHT.get(context);
        // Translucency is controlled by the separate opacity setting.
        return ColorUtils.setAlphaComponent(color, 255);
    }

    /**
     * Returns the stock (non-custom) drawer surface as an opaque color. This is the no-blur
     * fallback surface and is also used as the reference surface for label contrast.
     */
    @ColorInt
    public static int getStockBackgroundColor(Context context) {
        return context.getColor(Utilities.isDarkTheme(context)
                ? android.R.color.system_accent2_800
                : android.R.color.system_accent2_200);
    }

    /** Returns the user-selected drawer opacity as a fraction in {@code [0, 1]}. */
    public static float getDrawerOpacity(Context context) {
        int opacity = LauncherPrefs.APP_DRAWER_OPACITY.get(context);
        return Math.max(0, Math.min(100, opacity)) / 100f;
    }

    /**
     * Applies the user-selected drawer opacity to {@code color}. The opacity scales the color's
     * existing alpha, so already translucent surfaces (e.g. the stock blur panel) keep their
     * relative translucency, and opaque colors map linearly from 0% to 100%.
     */
    @ColorInt
    public static int applyDrawerOpacity(Context context, @ColorInt int color) {
        return applyOpacity(color, getDrawerOpacity(context));
    }

    /** Scales the alpha. */
    @ColorInt
    public static int applyOpacity(@ColorInt int color, float opacity) {
        return ColorUtils.setAlphaComponent(color, Math.round(Color.alpha(color) * opacity));
    }

    /** Custom drawer background with the user-selected opacity applied. */
    @ColorInt
    public static int getCustomBackgroundColorWithOpacity(Context context) {
        return applyDrawerOpacity(context, getCustomBackgroundColor(context));
    }

    /** Returns a legible primary content (label) color for the drawer background. */
    @ColorInt
    public static int getContentColor(Context context) {
        if (LauncherPrefs.ALL_APPS_DARK_TEXT.get(context)) {
            return context.getColor(R.color.all_apps_label_color_dark_forced);
        }
        final boolean custom = isCustomColorEnabled(context);
        final int drawerBackground = custom
                ? getCustomBackgroundColor(context)
                : getStockBackgroundColor(context);
        final int drawerText = custom
                ? getContrastingContentColor(context, drawerBackground)
                : Themes.getAttrColor(context, android.R.attr.textColorPrimary);
        final int wallpaperText = Themes.getAttrColor(context, R.attr.workspaceTextColor);

        // The workspace text color is chosen for the wallpaper, so it tells us whether the
        // wallpaper is light or dark.
        final int wallpaperEstimate = ColorUtils.setAlphaComponent(
                ColorUtils.calculateLuminance(wallpaperText) >= 0.5 ? Color.BLACK : Color.WHITE,
                255);
        final int effectiveBackground = ColorUtils.compositeColors(
                applyDrawerOpacity(context, drawerBackground), wallpaperEstimate);

        return ColorUtils.calculateContrast(drawerText, effectiveBackground)
                >= ColorUtils.calculateContrast(wallpaperText, effectiveBackground)
                ? drawerText : wallpaperText;
    }

    /**
     * Returns the surface used for the search bar and the fast-scroller letter bubbles, or
     * {@code fallback} (the stock surface) when no custom color is set.
     */
    @ColorInt
    public static int getSearchBackgroundColor(Context context, @ColorInt int fallback) {
        if (!isCustomColorEnabled(context)) {
            return fallback;
        }
        // Slightly lift the custom background so the search surface stays distinguishable.
        int background = getCustomBackgroundColor(context);
        float whiteBlend = ColorUtils.calculateLuminance(background) >= 0.5 ? 0.35f : 0.16f;
        return ColorUtils.blendARGB(background, Color.WHITE, whiteBlend);
    }

    /** Stock search/letter surface: {@code materialColorSurfaceBright}. */
    @ColorInt
    public static int getSearchBackgroundColor(Context context) {
        return getSearchBackgroundColor(context,
                context.getColor(R.color.materialColorSurfaceBright));
    }

    /**
     * Returns content with guaranteed contrast against {@link #getSearchBackgroundColor}, or
     * {@code fallback} (the stock content color) when no custom color is set.
     */
    @ColorInt
    public static int getSearchContentColor(Context context, @ColorInt int fallback) {
        if (!isCustomColorEnabled(context)) {
            return fallback;
        }
        return getContrastingContentColor(context, getSearchBackgroundColor(context));
    }

    @ColorInt
    private static int getContrastingContentColor(Context context, @ColorInt int background) {
        int dark = context.getColor(R.color.all_apps_label_color_dark_forced);
        int light = Color.WHITE;
        return ColorUtils.calculateContrast(dark, background)
                >= ColorUtils.calculateContrast(light, background) ? dark : light;
    }
}
