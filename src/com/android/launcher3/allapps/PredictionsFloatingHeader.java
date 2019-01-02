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

package com.android.launcher3.allapps;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.animation.Interpolator;

import com.android.launcher3.SettingsAppDrawer;
import com.android.launcher3.allapps.PredictionRowView.DividerType;
import com.android.launcher3.util.ComponentKeyMapper;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsContainerView.AdapterHolder;
import com.android.launcher3.allapps.FloatingHeaderView;
import com.android.launcher3.anim.PropertySetter;

import java.util.List;

public class PredictionsFloatingHeader extends FloatingHeaderView implements Insettable {

    public static final FloatProperty<PredictionsFloatingHeader> CONTENT_ALPHA = new FloatProperty<PredictionsFloatingHeader>("contentAlpha") {
        public void setValue(PredictionsFloatingHeader predictionsFloatingHeader, float f) {
            predictionsFloatingHeader.setContentAlpha(f);
        }

        public Float get(PredictionsFloatingHeader predictionsFloatingHeader) {
            return Float.valueOf(predictionsFloatingHeader.mContentAlpha);
        }
    };
    public float mContentAlpha = 1.0f;
    public final int mHeaderTopPadding;
    public boolean mIsCollapsed;
    public boolean mIsVerticalLayout;
    public PredictionRowView mPredictionRowView;
    public boolean mShowAllAppsLabel;

    public PredictionsFloatingHeader(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mHeaderTopPadding = context.getResources().getDimensionPixelSize(R.dimen.all_apps_header_top_padding);
    }

    public void onFinishInflate() {
        super.onFinishInflate();
        mPredictionRowView = (PredictionRowView) findViewById(R.id.predictions_row);
        setShowAllAppsLabel(true);
    }

    public void setup(AdapterHolder[] adapterHolderArr, boolean tabsHidden) {
        mPredictionRowView.setup(this, Utilities.getPrefs(Launcher.getLauncher(
            getContext())).getBoolean(SettingsAppDrawer.KEY_APP_SUGGESTIONS, true));
        mTabsHidden = tabsHidden;
        updateExpectedHeight();
        super.setup(adapterHolderArr, tabsHidden);
    }

    public final void updateExpectedHeight() {
        mPredictionRowView.setDividerType(DividerType.ALL_APPS_LABEL);
        mMaxTranslation = mPredictionRowView.getExpectedHeight();
    }

    public int getMaxTranslation() {
        if (mMaxTranslation == 0 && mTabsHidden) {
            return getResources().getDimensionPixelSize(R.dimen.all_apps_search_bar_bottom_padding);
        }
        int maxTrans = mMaxTranslation;
        if (maxTrans > 0) {
            if (mTabsHidden) {
                return maxTrans + getPaddingTop();
            }
        }
        return mMaxTranslation;
    }

    public PredictionRowView getPredictionRowView() {
        return mPredictionRowView;
    }

    public void setInsets(Rect rect) {
        DeviceProfile deviceProfile = Launcher.getLauncher(getContext()).getDeviceProfile();
        int devicePadding = deviceProfile.desiredWorkspaceLeftRightMarginPx + deviceProfile.cellLayoutPaddingLeftRightPx;
        PredictionRowView predictionRowView = mPredictionRowView;
        predictionRowView.setPadding(devicePadding, predictionRowView.getPaddingTop(), devicePadding, mPredictionRowView.getPaddingBottom());
        mIsVerticalLayout = deviceProfile.isVerticalBarLayout();
    }

    public void headerChanged() {
        int maxTrans = mMaxTranslation;
        updateExpectedHeight();
        if (mMaxTranslation != maxTrans) {
            Launcher.getLauncher(getContext()).getAppsView().setupHeader();
        }
    }

    public void applyScroll(int uncappedY, int currentY) {
        if (uncappedY < currentY - mHeaderTopPadding) {
            mPredictionRowView.setScrolledOut(true);
            return;
        }
        float translationY = (float) uncappedY;
        mPredictionRowView.setScrolledOut(false);
        mPredictionRowView.setScrollTranslation(translationY);
    }

    public void setContentVisibility(boolean hasHeader, boolean hasContent, PropertySetter propertySetter, Interpolator interpolator) {
        if (hasHeader && !hasContent && mIsCollapsed) {
            Launcher.getLauncher(getContext()).getAppsView().getSearchUiManager().resetSearch();
        }
        allowTouchForwarding(hasContent);
        propertySetter.setFloat(this, CONTENT_ALPHA, hasContent ? 1.0f : 0.0f, interpolator);
        mPredictionRowView.setContentVisibility(hasHeader, hasContent, propertySetter, interpolator);
    }

    public void setShowAllAppsLabel(boolean show) {
        if (mShowAllAppsLabel != show) {
            mShowAllAppsLabel = show;
            headerChanged();
        }
    }

    public final void setContentAlpha(float alpha) {
        mContentAlpha = alpha;
        mTabLayout.setAlpha(alpha);
    }

    public boolean hasVisibleContent() {
        return Utilities.getPrefs(Launcher.getLauncher(
            getContext())).getBoolean(SettingsAppDrawer.KEY_APP_SUGGESTIONS, true);
    }

    public void setCollapsed(boolean collapsed) {
        if (collapsed != mIsCollapsed) {
            mIsCollapsed = collapsed;
            mPredictionRowView.setCollapsed(collapsed);
            headerChanged();
        }
    }

    public void setPredictedApps(boolean isPredictions, List<ComponentKeyMapper> list) {
        mPredictionRowView.setPredictedApps(isPredictions, list);
    }
}
