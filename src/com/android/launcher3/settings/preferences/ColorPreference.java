/*
 * Copyright (C) 2023-2026 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.settings.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.launcher3.R;
import com.google.android.material.slider.Slider;
import com.google.android.material.tabs.TabLayout;

public class ColorPreference extends Preference {

    private int mColor = Color.WHITE;
    private View mPreviewView;

    public ColorPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.color_preference_widget);
    }

    public ColorPreference(Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        mPreviewView = holder.findViewById(R.id.color_preview);
        updatePreview(mPreviewView, mColor);
    }

    private void updatePreview(View view, int color) {
        if (view != null) {
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(color);
            shape.setStroke(2, Color.GRAY);
            view.setBackground(shape);
        }
    }

    @Override
    protected void onClick() {
        showColorPickerDialog();
    }

    private void showColorPickerDialog() {
        Context context = getContext();
        Context dialogContext = context;

        int themeId = context.getResources().getIdentifier(
                "Theme.Material3.DynamicColors.DayNight", "style", context.getPackageName());
        if (themeId == 0) {
            themeId = context.getResources().getIdentifier(
                    "Theme.MaterialComponents.DayNight", "style", context.getPackageName());
        }
        if (themeId == 0) {
            themeId = context.getResources().getIdentifier(
                    "Theme.AppCompat.DayNight", "style", context.getPackageName());
        }

        if (themeId != 0) {
            dialogContext = new android.view.ContextThemeWrapper(context, themeId);
        }

        View root = LayoutInflater.from(dialogContext).inflate(R.layout.color_picker_dialog, null);

        final View preview = root.findViewById(R.id.color_picker_preview);
        final TabLayout tabs = root.findViewById(R.id.color_picker_tabs);
        final LinearLayout hsbContainer = root.findViewById(R.id.hsb_container);
        final LinearLayout rgbContainer = root.findViewById(R.id.rgb_container);

        final Slider sliderHue = root.findViewById(R.id.slider_hue);
        final Slider sliderSaturation = root.findViewById(R.id.slider_saturation);
        final Slider sliderBrightness = root.findViewById(R.id.slider_brightness);

        final Slider sliderRed = root.findViewById(R.id.slider_red);
        final Slider sliderGreen = root.findViewById(R.id.slider_green);
        final Slider sliderBlue = root.findViewById(R.id.slider_blue);

        final EditText editHex = root.findViewById(R.id.edit_hex);

        final int[] currentColor = {mColor};
        final float[] hsv = new float[3];
        Color.colorToHSV(mColor, hsv);

        Runnable updateUI = () -> {
            updatePreview(preview, currentColor[0]);
            editHex.setText(String.format("#%08X", currentColor[0]));

            sliderHue.setValue(hsv[0]);
            sliderSaturation.setValue(hsv[1] * 100);
            sliderBrightness.setValue(hsv[2] * 100);

            sliderRed.setValue(Color.red(currentColor[0]));
            sliderGreen.setValue(Color.green(currentColor[0]));
            sliderBlue.setValue(Color.blue(currentColor[0]));
        };

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    hsbContainer.setVisibility(View.VISIBLE);
                    rgbContainer.setVisibility(View.GONE);
                } else {
                    hsbContainer.setVisibility(View.GONE);
                    rgbContainer.setVisibility(View.VISIBLE);
                }
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        Slider.OnChangeListener hsbListener = (slider, value, fromUser) -> {
            if (fromUser) {
                hsv[0] = sliderHue.getValue();
                hsv[1] = sliderSaturation.getValue() / 100f;
                hsv[2] = sliderBrightness.getValue() / 100f;
                int alpha = Color.alpha(currentColor[0]);
                currentColor[0] = Color.HSVToColor(alpha, hsv);

                updatePreview(preview, currentColor[0]);
                editHex.setText(String.format("#%08X", currentColor[0]));
                sliderRed.setValue(Color.red(currentColor[0]));
                sliderGreen.setValue(Color.green(currentColor[0]));
                sliderBlue.setValue(Color.blue(currentColor[0]));
            }
        };
        sliderHue.addOnChangeListener(hsbListener);
        sliderSaturation.addOnChangeListener(hsbListener);
        sliderBrightness.addOnChangeListener(hsbListener);

        Slider.OnChangeListener rgbListener = (slider, value, fromUser) -> {
            if (fromUser) {
                int r = (int) sliderRed.getValue();
                int g = (int) sliderGreen.getValue();
                int b = (int) sliderBlue.getValue();
                int alpha = Color.alpha(currentColor[0]);
                currentColor[0] = Color.argb(alpha, r, g, b);

                updatePreview(preview, currentColor[0]);
                editHex.setText(String.format("#%08X", currentColor[0]));
                Color.colorToHSV(currentColor[0], hsv);
                sliderHue.setValue(hsv[0]);
                sliderSaturation.setValue(hsv[1] * 100);
                sliderBrightness.setValue(hsv[2] * 100);
            }
        };
        sliderRed.addOnChangeListener(rgbListener);
        sliderGreen.addOnChangeListener(rgbListener);
        sliderBlue.addOnChangeListener(rgbListener);

        editHex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    int color = Color.parseColor(s.toString());
                    if (color != currentColor[0]) {
                        currentColor[0] = color;
                        updatePreview(preview, currentColor[0]);
                        // update sliders without triggering infinite loop
                        Color.colorToHSV(currentColor[0], hsv);
                        sliderHue.setValue(hsv[0]);
                        sliderSaturation.setValue(hsv[1] * 100);
                        sliderBrightness.setValue(hsv[2] * 100);
                        sliderRed.setValue(Color.red(currentColor[0]));
                        sliderGreen.setValue(Color.green(currentColor[0]));
                        sliderBlue.setValue(Color.blue(currentColor[0]));
                    }
                } catch (Exception e) {}
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        updateUI.run();

        new AlertDialog.Builder(dialogContext)
            .setTitle(getTitle())
            .setView(root)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                mColor = currentColor[0];
                persistInt(mColor);
                updatePreview(mPreviewView, mColor);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, Color.WHITE);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        mColor = getPersistedInt(defaultValue instanceof Integer ? (Integer) defaultValue : Color.WHITE);
    }
}
