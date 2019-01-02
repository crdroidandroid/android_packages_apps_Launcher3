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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.ColorUtils;
import android.text.Layout;
import android.text.Layout.Alignment;
import android.text.StaticLayout.Builder;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewConfiguration;
import android.view.animation.Interpolator;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import com.android.launcher3.SettingsAppDrawer;
import com.android.launcher3.shortcuts.ShortcutStore;
import com.android.launcher3.util.ComponentKeyMapper;

import com.android.launcher3.AppInfo;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.SettingsActivity;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.allapps.AllAppsStore.OnUpdateListener;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.keyboard.FocusIndicatorHelper;
import com.android.launcher3.keyboard.FocusIndicatorHelper.SimpleFocusIndicatorHelper;
import com.android.launcher3.logging.UserEventDispatcher.LogContainerProvider;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.util.Themes;

import com.android.quickstep.AnimatedFloat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PredictionRowView extends LinearLayout implements LogContainerProvider, OnUpdateListener, OnDeviceProfileChangeListener,
            ShortcutStore.OnUpdateListener {

    public static final Interpolator ALPHA_FACTOR_INTERPOLATOR = new Interpolator() {
        @Override
        public float getInterpolation(float alpha) {
            if (alpha < 0.8f) {
                return 0.0f;
            }
            return (alpha - 0.8f) / 0.2f;
        }
    };
    public static final Property<PredictionRowView, Integer> TEXT_ALPHA = new Property<PredictionRowView, Integer>(Integer.class, "textAlpha") {
        public void set(PredictionRowView predictionRowView, Integer value) {
            predictionRowView.setTextAlpha(value.intValue());
        }

        public Integer get(PredictionRowView predictionRowView) {
            return Integer.valueOf(predictionRowView.mIconCurrentTextAlpha);
        }
    };
    public Layout mAllAppsLabelLayout;
    public final int mAllAppsLabelTextColor;
    public int mAllAppsLabelTextCurrentAlpha;
    public final int mAllAppsLabelTextFullAlpha;
    public final TextPaint mAllAppsLabelTextPaint = new TextPaint();
    public final AnimatedFloat mContentAlphaFactor = new AnimatedFloat(new Runnable() {
        @Override
        public void run() {
            updateTranslationAndAlpha();
        }
    });
    public DividerType mDividerType;
    public FocusIndicatorHelper mFocusHelper;
    public int mIconCurrentTextAlpha;
    public int mIconFullTextAlpha;
    public int mIconTextColor;
    public boolean mIsCollapsed = false;
    public Launcher mLauncher;
    public View mLoadingProgress;
    public int mNumPredictedAppsPerRow;
    public final AnimatedFloat mOverviewScrollFactor = new AnimatedFloat(new Runnable() {
        @Override
        public void run() {
            updateTranslationAndAlpha();
        }
    });
    public Paint mPaint;
    public PredictionsFloatingHeader mParent;
    public final List<ComponentKeyMapper> mPredictedAppComponents = new ArrayList();
    public final ArrayList<ItemInfoWithIcon> mPredictedApps = new ArrayList();
    public boolean mPredictionsEnabled;
    public float mScrollTranslation = 0.0f;
    public boolean mScrolledOut;
    public int mStrokeColor;

    public enum DividerType {
        NONE,
        LINE,
        ALL_APPS_LABEL
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public PredictionRowView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        setOrientation(HORIZONTAL);
        setWillNotDraw(false);
        boolean isMainColorDark = Themes.getAttrBoolean(context, R.attr.isMainColorDark);
        mPaint = new Paint();
        mPaint.setColor(ContextCompat.getColor(context, isMainColorDark ? 
               R.color.all_apps_prediction_row_separator_dark : R.color.all_apps_prediction_row_separator));
        mPaint.setStrokeWidth((float) getResources().getDimensionPixelSize(R.dimen.all_apps_divider_height));
        mStrokeColor = mPaint.getColor();
        mFocusHelper = new SimpleFocusIndicatorHelper(this);
        mNumPredictedAppsPerRow = LauncherAppState.getIDP(context).numPredictions;
        mLauncher = Launcher.getLauncher(context);
        mLauncher.addOnDeviceProfileChangeListener(this);
        mIconTextColor = Themes.getAttrColor(context, android.R.attr.textColorSecondary);
        mIconFullTextAlpha = Color.alpha(mIconTextColor);
        mIconCurrentTextAlpha = mIconFullTextAlpha;
        mAllAppsLabelTextPaint.setColor(ContextCompat.getColor(context, isMainColorDark ? 
               R.color.all_apps_label_text_dark : R.color.all_apps_label_text));
        mAllAppsLabelTextColor = mAllAppsLabelTextPaint.getColor();
        mAllAppsLabelTextFullAlpha = Color.alpha(mAllAppsLabelTextColor);
        mAllAppsLabelTextCurrentAlpha = mAllAppsLabelTextFullAlpha;
        updateVisibility();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        getAppsStore().addUpdateListener(this);
        getAppsStore().registerIconContainer(this);
    }

    public AllAppsStore getAppsStore() {
        return mLauncher.getAppsView().getAppsStore();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getAppsStore().removeUpdateListener(this);
        getAppsStore().unregisterIconContainer(this);
    }

    public void setup(PredictionsFloatingHeader predictionsHeader, boolean isPredictions) {
        mParent = predictionsHeader;
        setPredictionsEnabled(isPredictions);
    }

    public void setPredictionsEnabled(boolean enabled) {
        if (enabled != mPredictionsEnabled) {
            mPredictionsEnabled = enabled;
            updateVisibility();
        }
    }

    public void updateVisibility() {
        if (mPredictionsEnabled) {
            setVisibility(View.VISIBLE);
        } else {
            setVisibility(View.GONE);
        }
    }

    @Override
    public void onMeasure(int i, int i2) {
        super.onMeasure(i, MeasureSpec.makeMeasureSpec(getExpectedHeight(), MeasureSpec.EXACTLY));
    }

    @Override
    public void dispatchDraw(Canvas canvas) {
        mFocusHelper.draw(canvas);
        super.dispatchDraw(canvas);
    }

    public int getExpectedHeight() {
        if (getVisibility() != View.VISIBLE) {
            return 0;
        }
        return (Launcher.getLauncher(getContext()).getDeviceProfile().allAppsCellHeightPx + getPaddingBottom()) + getPaddingTop();
    }

    public void setDividerType(DividerType dividerType) {
        int height = 0;
        if (mDividerType != dividerType) {
            mDividerType = dividerType;
        }

        if (dividerType == DividerType.ALL_APPS_LABEL) {
            mAllAppsLabelTextPaint.setAntiAlias(true);
            mAllAppsLabelTextPaint.setTypeface(Typeface.create("sans-serif-medium", 0));
            mAllAppsLabelTextPaint.setTextSize((float) getResources().getDimensionPixelSize(R.dimen.all_apps_label_text_size));
            CharSequence text = getResources().getText(R.string.all_apps_label);
            int length = text.length();
            TextPaint textPaint = mAllAppsLabelTextPaint;
            mAllAppsLabelLayout = Builder.obtain(text, 0, length, textPaint, Math.round(textPaint.measureText(text.toString()))).setAlignment(Alignment.ALIGN_CENTER).setMaxLines(1).setIncludePad(true).build();
            height = getAllAppsLayoutFullHeight();
        } else {
            mAllAppsLabelLayout = null;
            if (dividerType == DividerType.LINE) {
                height = getResources().getDimensionPixelSize(R.dimen.all_apps_prediction_row_divider_height);
            }
        }
        setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), height);
    }

    public List<ItemInfoWithIcon> getPredictedApps() {
        return mPredictedApps;
    }

    public void setPredictedApps(boolean isPredictions, List<ComponentKeyMapper> list) {
        setPredictionsEnabled(isPredictions);
        mPredictedAppComponents.clear();
        mPredictedAppComponents.addAll(list);
        onAppsUpdated();
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile deviceProfile) {
        removeAllViews();
        applyPredictionApps();
    }

    @Override
    public void onAppsUpdated() {
        mPredictedApps.clear();
        mPredictedApps.addAll(processPredictedAppComponents(mPredictedAppComponents));
        applyPredictionApps();
    }

    @Override
    public void onShortcutsUpdated() {
        mPredictedApps.clear();
        mPredictedApps.addAll(processPredictedAppComponents(mPredictedAppComponents));
        applyPredictionApps();
    }

    public void applyPredictionApps() {
        View view = mLoadingProgress;
        if (view != null) {
            removeView(view);
        }
        if (getChildCount() != mNumPredictedAppsPerRow) {
            while (getChildCount() > mNumPredictedAppsPerRow) {
                removeViewAt(0);
            }
            while (getChildCount() < mNumPredictedAppsPerRow) {
                BubbleTextView predictionIcon = (BubbleTextView) mLauncher.getLayoutInflater().inflate(R.layout.all_apps_icon, this, false);
                predictionIcon.setOnClickListener(ItemClickHandler.INSTANCE);
                predictionIcon.setOnLongClickListener(ItemLongClickListener.INSTANCE_ALL_APPS);
                predictionIcon.setLongPressTimeout(ViewConfiguration.getLongPressTimeout());
                predictionIcon.setOnFocusChangeListener(mFocusHelper);
                LayoutParams layoutParams = (LayoutParams) predictionIcon.getLayoutParams();
                layoutParams.height = getExpectedHeight();
                layoutParams.width = 0;
                layoutParams.weight = 1.0f;
                addView(predictionIcon);
            }
        }
        int size = mPredictedApps.size();
        int alphaComponent = ColorUtils.setAlphaComponent(mIconTextColor, mIconCurrentTextAlpha);
        for (int i = 0; i < getChildCount(); i++) {
            BubbleTextView predictionsView = (BubbleTextView) getChildAt(i);
            predictionsView.reset();
            if (size > i) {
                predictionsView.setVisibility(View.VISIBLE);
                if (mPredictedApps.get(i) instanceof AppInfo) {
                    predictionsView.applyFromApplicationInfo((AppInfo) mPredictedApps.get(i));
                } else if (mPredictedApps.get(i) instanceof ShortcutInfo) {
                    predictionsView.applyFromShortcutInfo((ShortcutInfo) mPredictedApps.get(i));
                }
                predictionsView.setTextColor(alphaComponent);
            } else {
                predictionsView.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
            }
        }
        if (size == 0) {
            if (mLoadingProgress == null) {
                mLoadingProgress = LayoutInflater.from(getContext()).inflate(R.layout.prediction_load_progress, this, false);
            }
            addView(mLoadingProgress);
        } else {
            mLoadingProgress = null;
        }
        mParent.headerChanged();
    }

    public List<ItemInfoWithIcon> processPredictedAppComponents(List<ComponentKeyMapper> list) {
        if (getAppsStore().getApps().isEmpty()) {
            return Collections.emptyList();
        }
        List<ItemInfoWithIcon> predictedApps = new ArrayList();
        for (ComponentKeyMapper app : list) {
            ItemInfoWithIcon app2 = app.getApp(getAppsStore());
            if (app2 != null) {
                predictedApps.add(app2);
            }
            if (predictedApps.size() == mNumPredictedAppsPerRow) {
                break;
            }
        }
        return predictedApps;
    }

    @Override
    public void onDraw(Canvas canvas) {
        DividerType dividerType = mDividerType;
        if (dividerType == DividerType.LINE) {
            int dimensionPixelSize = getResources().getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin);
            float height = (float) (getHeight() - (getPaddingBottom() / 2));
            canvas.drawLine((float) (getPaddingLeft() + dimensionPixelSize), height, (float) ((getWidth() - getPaddingRight()) - dimensionPixelSize), height, mPaint);
        } else if (dividerType == DividerType.ALL_APPS_LABEL) {
            drawAllAppsHeader(canvas);
        }
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, LauncherLogProto.Target target,
            LauncherLogProto.Target targetParent) {
        for (int i = 0; i < mPredictedApps.size(); i++) {
            ItemInfoWithIcon itemInfo = mPredictedApps.get(i);
            if (itemInfo == info) {
                targetParent.containerType = LauncherLogProto.ContainerType.PREDICTION;
                target.predictedRank = i;
                break;
            }
        }
    }

    public void setScrolledOut(boolean scrolledOut) {
        mScrolledOut = scrolledOut;
        updateTranslationAndAlpha();
    }

    public void setTextAlpha(int alpha) {
        int strokeColor;
        mIconCurrentTextAlpha = alpha;
        int alphaComponent = ColorUtils.setAlphaComponent(mIconTextColor, mIconCurrentTextAlpha);
        if (mLoadingProgress == null) {
            for (strokeColor = 0; strokeColor < getChildCount(); strokeColor++) {
                ((BubbleTextView) getChildAt(strokeColor)).setTextColor(alphaComponent);
            }
        }
        strokeColor = mStrokeColor;
        alpha = ColorUtils.setAlphaComponent(strokeColor, Math.round(((float) (Color.alpha(strokeColor) * alpha)) / 255.0f));
        if (alpha != mPaint.getColor()) {
            mPaint.setColor(alpha);
            mAllAppsLabelTextCurrentAlpha = Math.round((float) ((mAllAppsLabelTextFullAlpha * mIconCurrentTextAlpha) / mIconFullTextAlpha));
            mAllAppsLabelTextPaint.setColor(ColorUtils.setAlphaComponent(mAllAppsLabelTextColor, mAllAppsLabelTextCurrentAlpha));
            if (mDividerType != DividerType.NONE) {
                invalidate();
            }
        }
    }

    public void setScrollTranslation(float alpha) {
        mScrollTranslation = alpha;
        updateTranslationAndAlpha();
    }

    public void updateTranslationAndAlpha() {
        float translation = 1.0f;
        setTranslationY((1.0f - mOverviewScrollFactor.value) * mScrollTranslation);
        float interpolation = ALPHA_FACTOR_INTERPOLATOR.getInterpolation(mOverviewScrollFactor.value);
        float alpha = mContentAlphaFactor.value;
        float interpol = 1.0f - interpolation;
        if (mScrolledOut) {
            translation = 0.0f;
        }
        setAlpha(alpha * ((interpol * translation) + interpolation));
    }

    public void setContentVisibility(boolean hasHeader, boolean hasContent, PropertySetter propertySetter, Interpolator interpolator) {
        int alpha = 0;
        float f = 0.0f;
        boolean hasNoContent = false;
        boolean visible = getAlpha() > 0.0f;
        if (!hasHeader) {
            alpha = mIconCurrentTextAlpha;
        } else if (hasContent) {
            alpha = mIconFullTextAlpha;
        }
        if (visible) {
            propertySetter.setInt(this, TEXT_ALPHA, alpha, interpolator);
        } else {
            setTextAlpha(alpha);
        }
        if (hasHeader && !hasContent) {
            hasNoContent = true;
        }
        propertySetter.setFloat(mOverviewScrollFactor, AnimatedFloat.VALUE, hasNoContent ? 1.0f : 0.0f, Interpolators.LINEAR);
        AnimatedFloat animatedFloat = mContentAlphaFactor;
        Property<AnimatedFloat, Float> property = AnimatedFloat.VALUE;
        if (hasHeader) {
            f = 1.0f;
        }
        propertySetter.setFloat(animatedFloat, property, f, interpolator);
    }

    public void drawAllAppsHeader(Canvas canvas) {
        drawAllAppsHeader(canvas, this, mAllAppsLabelLayout);
    }

    public static void drawAllAppsHeader(Canvas canvas, View view, Layout allAppsLayout) {
        int width = (view.getWidth() / 2) - (allAppsLayout.getWidth() / 2);
        int height = (view.getHeight() - view.getResources().getDimensionPixelSize(R.dimen.all_apps_label_bottom_padding)) - allAppsLayout.getHeight();
        canvas.translate((float) width, (float) height);
        allAppsLayout.draw(canvas);
        canvas.translate((float) (-width), (float) (-height));
    }

    public int getAllAppsLayoutFullHeight() {
        return (mAllAppsLabelLayout.getHeight() + getResources().getDimensionPixelSize(R.dimen.all_apps_label_top_padding)) 
              + getResources().getDimensionPixelSize(R.dimen.all_apps_label_bottom_padding);
    }

    public void setCollapsed(boolean collapsed) {
        if (collapsed != mIsCollapsed) {
            mIsCollapsed = collapsed;
            updateVisibility();
        }
    }
}
