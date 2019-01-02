/*
 * Copyright (C) 2018 CypherOS
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

package com.android.launcher3.util;

import android.content.Context;

import com.android.launcher3.shortcuts.ShortcutStore;

import com.android.launcher3.AppInfo;
import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.ComponentKey;

public class ComponentKeyMapper {

    private Context mContext;
    protected ComponentKey mComponentKey;

    public ComponentKeyMapper(Context context, ComponentKey componentKey) {
        mContext = context;
        mComponentKey = componentKey;
    }

    public String getPackage() {
        return mComponentKey.componentName.getPackageName();
    }

    public String getComponentClass() {
        return mComponentKey.componentName.getClassName();
    }

    public ComponentKey getComponentKey() {
        return mComponentKey;
    }

    public String toString() {
        return mComponentKey.toString();
    }

    public ItemInfoWithIcon getApp(AllAppsStore allAppsStore) {
        AppInfo app = allAppsStore.getApp(mComponentKey);
        if (app != null) {
            return app;
        }
        if (mComponentKey instanceof ShortcutKey) {
            return (ShortcutInfo) ShortcutStore.getInstance(mContext).mComponentToShortcutMap.get((ShortcutKey) mComponentKey);
        }
        return null;
    }
}
