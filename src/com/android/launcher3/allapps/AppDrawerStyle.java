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

import androidx.annotation.Nullable;

import com.android.launcher3.LauncherPrefs;

/**
 * App drawer presentation styles.
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
}
