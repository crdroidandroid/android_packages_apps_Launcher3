/*
 * Copyright (C) 2026 VoltageOS
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
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.pageindicators.PageIndicatorDots;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OneUiPagedAllAppsView extends PagedView<PageIndicatorDots> {

    public interface OnActivePageChangedListener {
        void onActivePageChanged(@Nullable AllAppsRecyclerView recyclerView, int page);
    }

    private final ActivityContext mActivityContext;
    private final LayoutInflater mLayoutInflater;
    private final ArrayList<AppInfo> mApps = new ArrayList<>();
    private final ArrayList<AllAppsRecyclerView> mPageRecyclerViews = new ArrayList<>();
    private final Rect mPagePadding = new Rect();

    @Nullable private OnActivePageChangedListener mOnActivePageChangedListener;
    private int mLastItemsPerPage = -1;

    public OneUiPagedAllAppsView(Context context) {
        this(context, null);
    }

    public OneUiPagedAllAppsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OneUiPagedAllAppsView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mActivityContext = ActivityContext.lookupContext(context);
        mLayoutInflater = LayoutInflater.from(context);
        setClipToPadding(false);
        setClipChildren(false);
        setPageSpacing(0);
    }

    public void setPageIndicator(@Nullable PageIndicatorDots pageIndicator) {
        mPageIndicator = pageIndicator;
        if (mPageIndicator != null) {
            mPageIndicator.setMarkersCount(getChildCount());
            mPageIndicator.setActiveMarker(getNextPage());
        }
    }

    public void setOnActivePageChangedListener(@Nullable OnActivePageChangedListener listener) {
        mOnActivePageChangedListener = listener;
    }

    public void setApps(@NonNull List<AppInfo> apps) {
        mApps.clear();
        mApps.addAll(apps);
        post(() -> rebuildPages(false));
    }

    public void setPagePadding(@NonNull Rect padding) {
        mPagePadding.set(padding);
        applyPaddingToPages();
    }

    @Nullable
    public AllAppsRecyclerView getCurrentRecyclerView() {
        int page = getNextPage();
        return page >= 0 && page < mPageRecyclerViews.size() ? mPageRecyclerViews.get(page) : null;
    }

    @NonNull
    public List<AllAppsRecyclerView> getRecyclerViews() {
        return Collections.unmodifiableList(mPageRecyclerViews);
    }

    @Override
    protected void notifyPageSwitchListener(int prevPage) {
        super.notifyPageSwitchListener(prevPage);
        dispatchActivePageChanged();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        boolean isTablet = mActivityContext.getDeviceProfile().getDeviceProperties().isTablet();
        if (isTablet && (mPagePadding.left > 0 || mPagePadding.right > 0)) {
            canvas.save();
            canvas.clipRect(
                    getScrollX() + mPagePadding.left,
                    getScrollY(),
                    getScrollX() + getWidth() - mPagePadding.right,
                    getScrollY() + getHeight()
            );
            super.dispatchDraw(canvas);
            canvas.restore();
        } else {
            super.dispatchDraw(canvas);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) {
            post(() -> rebuildPages(true));
        }
    }

    @Override
    protected boolean canScroll(float absVScroll, float absHScroll) {
        return absHScroll > absVScroll && super.canScroll(absVScroll, absHScroll);
    }

    private void rebuildPages(boolean preservePage) {
        int itemsPerPage = getItemsPerPage();
        if (itemsPerPage <= 0) {
            return;
        }
        if (itemsPerPage == mLastItemsPerPage && getChildCount() > 0 && preservePage) {
            applyPaddingToPages();
            return;
        }
        mLastItemsPerPage = itemsPerPage;
        int pageToRestore = preservePage ? Math.min(getNextPage(), Math.max(0, getPageCount() - 1)) : 0;

        removeAllViews();
        mPageRecyclerViews.clear();

        int spanCount = Math.max(1, mActivityContext.getDeviceProfile().numShownAllAppsColumns);
        int adjustedCellHeight = getAdjustedCellHeight();
        int pageCount = Math.max(1, (int) Math.ceil(mApps.size() / (float) itemsPerPage));
        for (int page = 0; page < pageCount; page++) {
            int start = page * itemsPerPage;
            int end = Math.min(start + itemsPerPage, mApps.size());
            AllAppsRecyclerView recyclerView = new AllAppsRecyclerView(getContext()) {
                @Override
                public void scrollToTop() {
                    if (getScrollbar() != null) {
                        getScrollbar().setThumbOffsetY(0);
                    }
                    RecyclerView.LayoutManager layoutManager = getLayoutManager();
                    if (layoutManager instanceof GridLayoutManager) {
                        ((GridLayoutManager) layoutManager).scrollToPositionWithOffset(0, getPaddingTop());
                    }
                }
            };
            recyclerView.setId(View.generateViewId());
            recyclerView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT));
            recyclerView.setClipToPadding(true);
            recyclerView.setOverScrollMode(OVER_SCROLL_NEVER);
            recyclerView.setVerticalScrollBarEnabled(false);
            recyclerView.setHasFixedSize(true);
            recyclerView.setItemAnimator(null);
            recyclerView.setLayoutManager(new GridLayoutManager(getContext(), spanCount) {
                @Override
                public boolean canScrollVertically() {
                    return false;
                }
            });
            recyclerView.setAdapter(new PageAdapter(mApps.subList(start, end), adjustedCellHeight));
            mPageRecyclerViews.add(recyclerView);
            addView(recyclerView);
        }

        applyPaddingToPages();
        setCurrentPage(Math.min(pageToRestore, Math.max(0, getChildCount() - 1)));
        if (mPageIndicator != null) {
            mPageIndicator.setMarkersCount(getChildCount());
            mPageIndicator.setActiveMarker(getNextPage());
        }
        dispatchActivePageChanged();
    }

    private void applyPaddingToPages() {
        for (AllAppsRecyclerView recyclerView : mPageRecyclerViews) {
            recyclerView.setPadding(mPagePadding.left, mPagePadding.top,
                    mPagePadding.right, mPagePadding.bottom);
        }
    }

    private void dispatchActivePageChanged() {
        if (mOnActivePageChangedListener != null) {
            mOnActivePageChangedListener.onActivePageChanged(getCurrentRecyclerView(), getNextPage());
        }
    }

    private int getRowsPerPage() {
        return Math.max(1, mActivityContext.getDeviceProfile().inv.numRows);
    }

    private int getAdjustedCellHeight() {
        int height = getMeasuredHeight();
        int baseCellHeight = Math.max(1, mActivityContext.getDeviceProfile().getAllAppsProfile().getCellHeightPx());
        if (height <= 0) {
            return baseCellHeight;
        }
        int rows = getRowsPerPage();
        if (rows <= 0) {
            return baseCellHeight;
        }
        int availableHeight = Math.max(0, height - mPagePadding.top - mPagePadding.bottom);

        return availableHeight / rows;
    }

    private int getItemsPerPage() {
        int width = getMeasuredWidth();
        if (width <= 0 || getMeasuredHeight() <= 0) {
            return 0;
        }
        int cols = Math.max(1, mActivityContext.getDeviceProfile().numShownAllAppsColumns);
        int rows = getRowsPerPage();
        return rows * cols;
    }

    private class PageAdapter extends RecyclerView.Adapter<PageAdapter.IconHolder> {
        private final List<AppInfo> mPageApps;
        private final int mCellHeight;
        private final int mTextColor;
        private final int mLayoutRes;

        PageAdapter(List<AppInfo> pageApps, int cellHeight) {
            mPageApps = pageApps;
            mCellHeight = cellHeight;
            boolean forceDarkText = LauncherPrefs.ALL_APPS_DARK_TEXT.get(mActivityContext.asContext());
            mTextColor = forceDarkText
                    ? mActivityContext.asContext().getResources().getColor(
                            R.color.all_apps_label_color_dark_forced, null)
                    : Themes.getAttrColor(mActivityContext.asContext(), android.R.attr.textColorPrimary);
            mLayoutRes = LauncherPrefs.ENABLE_TWOLINE_ALLAPPS_TOGGLE.get(mActivityContext.asContext())
                    ? R.layout.all_apps_icon_twoline : R.layout.all_apps_icon;
        }

        @Override
        public int getItemCount() {
            return mPageApps.size();
        }

        @NonNull
        @Override
        public IconHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(mLayoutRes, parent, false);
            icon.setLongPressTimeoutFactor(1f);
            icon.setOnClickListener(mActivityContext.getItemOnClickListener());
            icon.setOnLongClickListener(mActivityContext.getAllAppsItemLongClickListener());
            ViewGroup.LayoutParams lp = icon.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = mCellHeight;
            return new IconHolder(icon);
        }

        @Override
        public void onBindViewHolder(@NonNull IconHolder holder, int position) {
            BubbleTextView icon = holder.mIcon;
            icon.reset();
            icon.setTextColor(mTextColor);
            icon.applyFromApplicationInfo(mPageApps.get(position));
        }

        class IconHolder extends RecyclerView.ViewHolder {
            final BubbleTextView mIcon;

            IconHolder(BubbleTextView icon) {
                super(icon);
                mIcon = icon;
            }
        }
    }
}
