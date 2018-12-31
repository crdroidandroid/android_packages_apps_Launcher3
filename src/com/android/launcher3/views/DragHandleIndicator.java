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
package com.android.launcher3.views;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;

import com.android.launcher3.qsb.HotseatQsbView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.quickstep.views.QuickstepDragIndicator;

public class DragHandleIndicator extends QuickstepDragIndicator {
    public DragHandleIndicator(Context context) {
        super(context);
    }

    public DragHandleIndicator(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public DragHandleIndicator(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
    }

    @Override
    public int getPortraitBottomMargin(DeviceProfile deviceProfile, Rect rect) {
        return HotseatQsbView.getHotseatPadding(mLauncher) + getResources().getDimensionPixelSize(R.dimen.qsb_widget_height);
    }
}
