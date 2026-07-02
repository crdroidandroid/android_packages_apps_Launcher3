/*
 * Copyright (C) 2024-2026 Lunaris AOSP
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
package com.android.launcher3.customization;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.inputmethod.InputMethodManager;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseActivity;
import com.android.launcher3.R;
import com.android.launcher3.icons.pack.IconPack;
import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.views.AbstractSlideInView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR;

public class IconPickerBottomSheet extends AbstractSlideInView<BaseActivity> {
    private static final int DEFAULT_CLOSE_DURATION = 200;
    private static final long SEARCH_DEBOUNCE_MS = 300;

    public interface OnIconChosen {
        void onIconChosen();
    }

    private View mContentView;
    private RecyclerView mRecyclerView;
    private TextView mTitle;
    private EditText mSearchField;
    private final IconPackManager mManager;
    private ComponentKey mKey;
    private OnIconChosen mCallback;

    private final Handler mDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable mPendingFilter;
    private List<IconPack.IconEntry> mAllEntriesForCurrentPack;
    private IconGridAdapter mCurrentGridAdapter;
    private Resources mCurrentPackRes;
    private String mCurrentPackPackage;

    public IconPickerBottomSheet(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IconPickerBottomSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mManager = IconPackManager.get(context);
        setWillNotDraw(false);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.icon_picker_content);
        mContentView = mContent;
        mRecyclerView = findViewById(R.id.icon_picker_recycler_view);
        mTitle = findViewById(R.id.icon_picker_title);
        mSearchField = findViewById(R.id.icon_picker_search);
        mSearchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                scheduleFilter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        mRecyclerView.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                hideKeyboard();
            }
            return false;
        });
        setContentBackgroundWithParent(
                getContext().getDrawable(R.drawable.bg_rounded_corner_bottom_sheet), mContent);
    }

    public void show(ComponentKey key, OnIconChosen callback) {
        mKey = key;
        mCallback = callback;
        mTitle.setText(R.string.app_info_custom_icon_title);
        showPackList();
        attachToContainer();
        mIsOpen = false;
        animateOpenSelf();
    }

    private void animateOpenSelf() {
        if (mIsOpen || mOpenCloseAnimation.getAnimationPlayer().isRunning()) {
            return;
        }
        mIsOpen = true;
        setUpDefaultOpenAnimation().start();
    }

    private void showPackList() {
        mSearchField.setVisibility(View.GONE);
        mSearchField.setText("");
        Map<String, CharSequence> packs = mManager.getProviderNames();
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerView.setAdapter(new PackListAdapter(packs, this::showIconGrid));
    }

    private void showIconGrid(String packPackage) {
        mCurrentPackPackage = packPackage;
        mSearchField.setText("");
        mSearchField.setVisibility(View.VISIBLE);
        THREAD_POOL_EXECUTOR.execute(() -> {
            List<IconPack.IconEntry> entries = mManager.getAllIconEntries(packPackage);
            Resources packRes;
            try {
                packRes = getContext().getPackageManager().getResourcesForApplication(packPackage);
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                packRes = getContext().getResources();
            }
            final Resources finalPackRes = packRes;
            MAIN_EXECUTOR.execute(() -> {
                mAllEntriesForCurrentPack = entries;
                mCurrentPackRes = finalPackRes;
                mRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 5));
                mCurrentGridAdapter =
                        new IconGridAdapter(packPackage, entries, finalPackRes, this::onIconPicked);
                mRecyclerView.setAdapter(mCurrentGridAdapter);
            });
        });
    }

    private void scheduleFilter(String rawQuery) {
        if (mPendingFilter != null) {
            mDebounceHandler.removeCallbacks(mPendingFilter);
        }
        mPendingFilter = () -> applyFilter(rawQuery.trim());
        mDebounceHandler.postDelayed(mPendingFilter, SEARCH_DEBOUNCE_MS);
    }

    private void applyFilter(String trimmedQuery) {
        if (mAllEntriesForCurrentPack == null || mCurrentGridAdapter == null) return;

        List<IconPack.IconEntry> filtered;
        if (trimmedQuery.isEmpty()) {
            filtered = mAllEntriesForCurrentPack;
        } else {
            filtered = new ArrayList<>();
            for (IconPack.IconEntry entry : mAllEntriesForCurrentPack) {
                if (entry.drawableName.toLowerCase().contains(trimmedQuery.toLowerCase())) {
                    filtered.add(entry);
                }
            }
        }
        mCurrentGridAdapter = new IconGridAdapter(
                mCurrentPackPackage, filtered, mCurrentPackRes, this::onIconPicked);
        mRecyclerView.setAdapter(mCurrentGridAdapter);
    }

    private void onIconPicked(String packPackage, IconPack.IconEntry entry) {
        hideKeyboard();
        IconDatabase.setExplicitIconForComponent(getContext(), mKey, packPackage, entry.drawableName);
        if (mCallback != null) {
            mCallback.onIconChosen();
        }
        close(true);
    }

    private void hideKeyboard() {
        if (mSearchField == null) return;
        InputMethodManager imm = (InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mSearchField.getWindowToken(), 0);
        }
        mSearchField.clearFocus();
    }

    @Override
    protected void handleClose(boolean animate) {
        hideKeyboard();
        handleClose(animate, DEFAULT_CLOSE_DURATION);
    }

    @Override
    protected boolean isOfType(@AbstractFloatingView.FloatingViewType int type) {
        return (type & AbstractFloatingView.TYPE_ICON_PICKER_BOTTOM_SHEET) != 0;
    }

    @Override
    protected float getShiftRange() {
        return mContent.getHeight();
    }

    @Override
    protected Pair<View, String> getAccessibilityTarget() {
        return Pair.create(mTitle, getContext().getString(R.string.app_info_custom_icon_title));
    }

    private static class PackListAdapter extends RecyclerView.Adapter<PackListAdapter.VH> {
        interface OnPackClick { void onClick(String packPackage); }

        private final List<Map.Entry<String, CharSequence>> mItems;
        private final OnPackClick mListener;

        PackListAdapter(Map<String, CharSequence> packs, OnPackClick listener) {
            mItems = new ArrayList<>(packs.entrySet());
            mListener = listener;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            Map.Entry<String, CharSequence> entry = mItems.get(position);
            holder.text.setText(entry.getValue());
            holder.itemView.setOnClickListener(v -> mListener.onClick(entry.getKey()));
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView text;
            VH(View itemView) {
                super(itemView);
                text = itemView.findViewById(android.R.id.text1);
            }
        }
    }

    private static class IconGridAdapter extends RecyclerView.Adapter<IconGridAdapter.VH> {
        interface OnIconClick { void onClick(String packPackage, IconPack.IconEntry entry); }

        private final String mPackPackage;
        private final List<IconPack.IconEntry> mEntries;
        private final OnIconClick mListener;
        private final Resources mPackRes;

        IconGridAdapter(String packPackage, List<IconPack.IconEntry> entries,
                Resources packRes, OnIconClick listener) {
            mPackPackage = packPackage;
            mEntries = entries;
            mPackRes = packRes;
            mListener = listener;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            int size = parent.getContext().getResources()
                    .getDimensionPixelSize(R.dimen.icon_picker_cell_size);
            iv.setLayoutParams(new ViewGroup.LayoutParams(size, size));
            int pad = parent.getContext().getResources()
                    .getDimensionPixelSize(R.dimen.icon_picker_cell_padding);
            iv.setPadding(pad, pad, pad, pad);
            return new VH(iv);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            IconPack.IconEntry entry = mEntries.get(position);
            Drawable d;
            try {
                d = mPackRes.getDrawable(entry.resId, null);
            } catch (Resources.NotFoundException e) {
                d = null;
            }
            holder.image.setImageDrawable(d);
            holder.itemView.setOnClickListener(v -> mListener.onClick(mPackPackage, entry));
        }

        @Override
        public int getItemCount() {
            return mEntries.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            ImageView image;
            VH(View itemView) {
                super(itemView);
                image = (ImageView) itemView;
            }
        }
    }
}