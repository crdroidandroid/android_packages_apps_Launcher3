/*
 * Copyright (C) 2023 crDroid Android Project
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
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.launcher3.R;

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
        updatePreview();
    }

    private void updatePreview() {
        if (mPreviewView != null) {
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(mColor);
            shape.setStroke(2, Color.GRAY);
            mPreviewView.setBackground(shape);
        }
    }

    @Override
    protected void onClick() {
        showColorPickerDialog();
    }

    private void showColorPickerDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (16 * getContext().getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        final EditText input = new EditText(getContext());
        input.setText(String.format("#%08X", mColor));
        layout.addView(input);

        final ImageView preview = new ImageView(getContext());
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (int) (50 * getContext().getResources().getDisplayMetrics().density));
        lp.topMargin = padding;
        preview.setLayoutParams(lp);
        updateDialogPreview(preview, mColor);
        layout.addView(preview);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    int color = Color.parseColor(s.toString());
                    updateDialogPreview(preview, color);
                } catch (Exception e) {
                    // Invalid color
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        new AlertDialog.Builder(getContext())
            .setTitle(getTitle())
            .setView(layout)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                try {
                    int color = Color.parseColor(input.getText().toString());
                    mColor = color;
                    persistInt(mColor);
                    updatePreview();
                } catch (Exception e) {
                    // Keep old color
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void updateDialogPreview(ImageView view, int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(color);
        shape.setStroke(2, Color.GRAY);
        view.setImageDrawable(shape);
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
