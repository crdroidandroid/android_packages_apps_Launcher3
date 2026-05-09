/*
 * Copyright (C) 2026 VoltageOS
 *           (C) 2026 crDroid Android Project
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
import android.view.MotionEvent;
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

    private static final int RECYCLED_VIEW_POOL_SIZE = 64;

    private final ActivityContext mActivityContext;
    private final LayoutInflater mLayoutInflater;
    private final ArrayList<AppInfo> mApps = new ArrayList<>();
    private final ArrayList<AllAppsRecyclerView> mPageRecyclerViews = new ArrayList<>();
    private final Rect mPagePadding = new Rect();

    private final RecyclerView.RecycledViewPool mSharedPool = new RecyclerView.RecycledViewPool();

    @Nullable private OnActivePageChangedListener mOnActivePageChangedListener;
    private int mLastItemsPerPage = -1;
    private int mLastSpanCount = -1;
    private int mLastCellHeight = -1;
    private int mLastAppCount = -1;

    private boolean mLayersEnabled = false;

    private final Runnable mRebuildRunnable = () -> rebuildPages(false);
    private final Runnable mRebuildPreserveRunnable = () -> rebuildPages(true);

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
        setMotionEventSplittingEnabled(false);

        mSharedPool.setMaxRecycledViews(0 /* viewType */, RECYCLED_VIEW_POOL_SIZE);
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
        boolean appCountChanged = apps.size() != mLastAppCount;
        mApps.clear();
        mApps.addAll(apps);
        mLastAppCount = apps.size();

        removeCallbacks(mRebuildRunnable);
        removeCallbacks(mRebuildPreserveRunnable);

        if (appCountChanged) {
            mLastItemsPerPage = -1;
        }

        if (getMeasuredWidth() > 0 && getMeasuredHeight() > 0) {
            rebuildPages(false);
        } else {
            post(mRebuildRunnable);
        }
    }

    public void setPagePadding(@NonNull Rect padding) {
        if (mPagePadding.equals(padding)) {
            return;
        }
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
    protected void onPageBeginTransition() {
        super.onPageBeginTransition();
        enableHardwareLayers();
    }

    @Override
    protected void onPageEndTransition() {
        super.onPageEndTransition();
        disableHardwareLayers();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean intercepted = super.onInterceptTouchEvent(ev);
        if (intercepted) {
            enableHardwareLayers();
        }
        return intercepted;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean handled = super.onTouchEvent(ev);
        if (ev.getActionMasked() == MotionEvent.ACTION_MOVE) {
            enableHardwareLayers();
        }
        return handled;
    }

    private void enableHardwareLayers() {
        if (mLayersEnabled) {
            return;
        }
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getLayerType() != LAYER_TYPE_HARDWARE) {
                child.setLayerType(LAYER_TYPE_HARDWARE, null);
            }
        }
        mLayersEnabled = true;
    }

    private void disableHardwareLayers() {
        if (!mLayersEnabled) {
            return;
        }
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getLayerType() != LAYER_TYPE_NONE) {
                child.setLayerType(LAYER_TYPE_NONE, null);
            }
        }
        mLayersEnabled = false;
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
            removeCallbacks(mRebuildPreserveRunnable);
            post(mRebuildPreserveRunnable);
        }
    }

    @Override
    protected boolean canScroll(float absVScroll, float absHScroll) {
        return absHScroll > absVScroll && super.canScroll(absVScroll, absHScroll);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(mRebuildRunnable);
        removeCallbacks(mRebuildPreserveRunnable);
    }

    private void rebuildPages(boolean preservePage) {
        int itemsPerPage = getItemsPerPage();
        if (itemsPerPage <= 0) {
            return;
        }
        int spanCount = Math.max(1, mActivityContext.getDeviceProfile().numShownAllAppsColumns);
        int adjustedCellHeight = getAdjustedCellHeight();

        if (preservePage
                && itemsPerPage == mLastItemsPerPage
                && spanCount == mLastSpanCount
                && adjustedCellHeight == mLastCellHeight
                && getChildCount() > 0
                && !mPageRecyclerViews.isEmpty()) {
            applyPaddingToPages();
            return;
        }
        mLastItemsPerPage = itemsPerPage;
        mLastSpanCount = spanCount;
        mLastCellHeight = adjustedCellHeight;

        int pageToRestore = preservePage
                ? Math.min(getNextPage(), Math.max(0, getPageCount() - 1)) : 0;

        disableHardwareLayers();

        removeAllViews();
        mPageRecyclerViews.clear();

        int pageCount = Math.max(1, (int) Math.ceil(mApps.size() / (float) itemsPerPage));
        for (int page = 0; page < pageCount; page++) {
            int start = page * itemsPerPage;
            int end = Math.min(start + itemsPerPage, mApps.size());
            AllAppsRecyclerView recyclerView = createPageRecyclerView(spanCount, itemsPerPage,
                    adjustedCellHeight, mApps.subList(start, end));
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

    private AllAppsRecyclerView createPageRecyclerView(int spanCount, int itemsPerPage,
            int cellHeight, List<AppInfo> pageApps) {
        AllAppsRecyclerView recyclerView = new AllAppsRecyclerView(getContext()) {
            @Override
            public void scrollToTop() {
                if (getScrollbar() != null) {
                    getScrollbar().setThumbOffsetY(0);
                }
                RecyclerView.LayoutManager layoutManager = getLayoutManager();
                if (layoutManager instanceof GridLayoutManager) {
                    ((GridLayoutManager) layoutManager).scrollToPositionWithOffset(0,
                            getPaddingTop());
                }
            }
        };
        recyclerView.setId(View.generateViewId());
        recyclerView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        recyclerView.setClipToPadding(true);
        recyclerView.setOverScrollMode(OVER_SCROLL_NEVER);
        recyclerView.setVerticalScrollBarEnabled(false);
        recyclerView.setHorizontalScrollBarEnabled(false);
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        recyclerView.setNestedScrollingEnabled(false);
        recyclerView.setItemViewCacheSize(itemsPerPage);
        recyclerView.setRecycledViewPool(mSharedPool);

        GridLayoutManager lm = new GridLayoutManager(getContext(), spanCount) {
            @Override
            public boolean canScrollVertically() {
                return false;
            }

            @Override
            public boolean canScrollHorizontally() {
                return false;
            }

            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        lm.setItemPrefetchEnabled(false);
        recyclerView.setLayoutManager(lm);
        recyclerView.setAdapter(new PageAdapter(pageApps, cellHeight));
        return recyclerView;
    }

    private void applyPaddingToPages() {
        for (AllAppsRecyclerView recyclerView : mPageRecyclerViews) {
            if (recyclerView.getPaddingLeft() != mPagePadding.left
                    || recyclerView.getPaddingTop() != mPagePadding.top
                    || recyclerView.getPaddingRight() != mPagePadding.right
                    || recyclerView.getPaddingBottom() != mPagePadding.bottom) {
                recyclerView.setPadding(mPagePadding.left, mPagePadding.top,
                        mPagePadding.right, mPagePadding.bottom);
            }
        }
    }

    private void dispatchActivePageChanged() {
        if (mOnActivePageChangedListener != null) {
            mOnActivePageChangedListener.onActivePageChanged(getCurrentRecyclerView(),
                    getNextPage());
        }
    }

    private int getRowsPerPage() {
        return Math.max(1, mActivityContext.getDeviceProfile().inv.numRows);
    }

    private int getAdjustedCellHeight() {
        int height = getMeasuredHeight();
        int baseCellHeight = Math.max(1,
                mActivityContext.getDeviceProfile().getAllAppsProfile().getCellHeightPx());
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
        private final View.OnClickListener mClickListener;
        private final View.OnLongClickListener mLongClickListener;

        PageAdapter(List<AppInfo> pageApps, int cellHeight) {
            mPageApps = pageApps;
            mCellHeight = cellHeight;
            Context ctx = mActivityContext.asContext();
            boolean forceDarkText = LauncherPrefs.ALL_APPS_DARK_TEXT.get(ctx);
            mTextColor = forceDarkText
                    ? ctx.getResources().getColor(R.color.all_apps_label_color_dark_forced, null)
                    : Themes.getAttrColor(ctx, android.R.attr.textColorPrimary);
            mLayoutRes = LauncherPrefs.ENABLE_TWOLINE_ALLAPPS_TOGGLE.get(ctx)
                    ? R.layout.all_apps_icon_twoline : R.layout.all_apps_icon;
            mClickListener = mActivityContext.getItemOnClickListener();
            mLongClickListener = mActivityContext.getAllAppsItemLongClickListener();
        }

        @Override
        public int getItemCount() {
            return mPageApps.size();
        }

        @NonNull
        @Override
        public IconHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(mLayoutRes, parent,
                    false);
            icon.setLongPressTimeoutFactor(1f);
            icon.setOnClickListener(mClickListener);
            icon.setOnLongClickListener(mLongClickListener);
            ViewGroup.LayoutParams lp = icon.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = mCellHeight;
            return new IconHolder(icon);
        }

        @Override
        public void onBindViewHolder(@NonNull IconHolder holder, int position) {
            BubbleTextView icon = holder.mIcon;
            ViewGroup.LayoutParams lp = icon.getLayoutParams();
            if (lp != null && lp.height != mCellHeight) {
                lp.height = mCellHeight;
                icon.setLayoutParams(lp);
            }
            icon.reset();
            icon.setTextColor(mTextColor);
            icon.applyFromApplicationInfo(mPageApps.get(position));
        }

        @Override
        public void onViewRecycled(@NonNull IconHolder holder) {
            super.onViewRecycled(holder);
            holder.mIcon.reset();
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
