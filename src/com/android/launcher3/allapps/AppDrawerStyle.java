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
     * <p>Fullscreen drawers are large containers, so use the low-emphasis Material surface
     * instead of {@code surfaceDim}. The latter intentionally darkens the entire screen in light
     * mode and becomes muddy after the user-selected transparency is applied over the wallpaper.
     */
    public static int getThemedBackgroundColor(Context context) {
        return isFullscreen(context)
                ? context.getColor(R.color.materialColorSurfaceContainerLow)
                : Themes.getAttrColor(context, R.attr.allAppsScrimColor);
    }
}
