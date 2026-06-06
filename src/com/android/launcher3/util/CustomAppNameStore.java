/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.android.launcher3.model.data.ItemInfo;

/** Persists user-defined app display names for launcher items. */
public final class CustomAppNameStore {

    private static final String CUSTOM_NAMES_PREFS = "custom_app_names";

    private CustomAppNameStore() { }

    @Nullable
    public static String customNameKey(ItemInfo info) {
        ComponentName cn = info.getTargetComponent();
        if (cn == null) {
            return null;
        }
        return cn.getPackageName() + "/" + cn.getClassName() + "/" + info.user.hashCode();
    }

    public static void saveCustomName(Context context, ItemInfo info, @Nullable String name) {
        String key = customNameKey(info);
        if (key == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(CUSTOM_NAMES_PREFS,
                Context.MODE_PRIVATE);
        if (name == null) {
            prefs.edit().remove(key).apply();
        } else {
            prefs.edit().putString(key, name).apply();
        }
    }

    @Nullable
    public static String getCustomName(Context context, ItemInfo info) {
        String key = customNameKey(info);
        if (key == null) {
            return null;
        }
        return context.getSharedPreferences(CUSTOM_NAMES_PREFS, Context.MODE_PRIVATE)
                .getString(key, null);
    }
}
