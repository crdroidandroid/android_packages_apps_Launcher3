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

package com.android.launcher3;

import android.content.ComponentName;
import android.view.View;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Workspace.ItemOperator;
import com.android.launcher3.util.ComponentKey;

public class ComponentItemOperator implements ItemOperator {

    public ComponentKey mComponentKey;

    public ComponentItemOperator(ComponentKey componentKey) {
        mComponentKey = componentKey;
    }

    @Override
    public boolean evaluate(ItemInfo info, View view) {
        Object packageName;
        ComponentName componentName;
        Object obj = null;
        if (info != null) {
            ComponentName targetComponent = info.getTargetComponent();
            if (targetComponent != null) {
                packageName = targetComponent.getPackageName();
                componentName = mComponentKey.componentName;
                if (packageName.equals(componentName.getPackageName())) {
                    if (info != null) {
                        obj = info.user;
                    }
                    if (obj.equals(mComponentKey.user)) {
                        return true;
                    }
                }
                return false;
            }
        }
        packageName = null;
        componentName = mComponentKey.componentName;
        return false;
    }
}