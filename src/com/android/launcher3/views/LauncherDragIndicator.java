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
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ControlType;

public class LauncherDragIndicator extends ImageView implements OnClickListener, Insettable {

    private static final int SETTINGS = R.string.settings_button_text;
    private static final int WALLPAPERS = R.string.wallpaper_button_text;
    private static final int WIDGETS = R.string.widget_button_text;
    public Launcher mLauncher;

    public LauncherDragIndicator(Context context) {
        this(context, null);
    }

    public LauncherDragIndicator(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public LauncherDragIndicator(Context context, AttributeSet attributeSet, int res) {
        super(context, attributeSet, res);
        mLauncher = Launcher.getLauncher(context);
        setOnClickListener(this);
    }

    @Override
    public void setInsets(Rect rect) {
        DeviceProfile dp = mLauncher.getDeviceProfile();
        LayoutParams layoutParams = (LayoutParams) getLayoutParams();
        if (dp.isVerticalBarLayout()) {
            if (dp.isSeascape()) {
                layoutParams.leftMargin = dp.hotseatBarSidePaddingEndPx;
                layoutParams.rightMargin = rect.right;
                layoutParams.gravity = 85;
            } else {
                layoutParams.leftMargin = rect.left;
                layoutParams.rightMargin = dp.hotseatBarSidePaddingEndPx;
                layoutParams.gravity = 83;
            }
            layoutParams.bottomMargin = dp.workspacePadding.bottom;
            setImageResource(R.drawable.all_apps_handle_landscape);
        } else {
            layoutParams.rightMargin = 0;
            layoutParams.leftMargin = 0;
            layoutParams.gravity = 81;
            layoutParams.bottomMargin = getPortraitBottomMargin(dp, rect);
            setImageResource(R.drawable.ic_drag_indicator);
        }
        int pageIndicatorSize = dp.pageIndicatorSizePx;
        layoutParams.height = pageIndicatorSize;
        layoutParams.width = pageIndicatorSize;
        setLayoutParams(layoutParams);
    }

    public int getPortraitBottomMargin(DeviceProfile deviceProfile, Rect rect) {
        return (deviceProfile.hotseatBarSizePx + rect.bottom) - deviceProfile.pageIndicatorSizePx;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo accessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(accessibilityNodeInfo);
        initCustomActions(accessibilityNodeInfo);
    }

    public void initCustomActions(AccessibilityNodeInfo info) {
        Context context = getContext();
        if (Utilities.isWallpaperAllowed(context)) {
            info.addAction(new AccessibilityAction(WALLPAPERS, context.getText(WALLPAPERS)));
        }
        info.addAction(new AccessibilityAction(WIDGETS, context.getText(WIDGETS)));
        info.addAction(new AccessibilityAction(SETTINGS, context.getText(SETTINGS)));
    }

    public boolean performAccessibilityAction(int action, Bundle bundle) {
        if (action == WALLPAPERS) {
            return OptionsPopupView.startWallpaperPicker(this);
        }
        if (action == WIDGETS) {
            return OptionsPopupView.onWidgetsClicked(this);
        }
        if (action == SETTINGS) {
            return OptionsPopupView.startSettings(this);
        }
        return super.performAccessibilityAction(action, bundle);
    }

    @Override
    public void onClick(View view) {
        if (!mLauncher.isInState(LauncherState.ALL_APPS)) {
            mLauncher.getUserEventDispatcher().logActionOnControl(
                Action.Touch.TAP, ControlType.ALL_APPS_BUTTON);
            mLauncher.getStateManager().goToState(LauncherState.ALL_APPS);
        }
    }
}
