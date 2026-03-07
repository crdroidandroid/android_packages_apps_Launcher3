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
import com.android.launcher3.touch.CustomActionsListener;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Horizontally paged, fullscreen All Apps grid (One UI style). Every page is a non-scrolling
 * {@link AllAppsRecyclerView} holding {@code rows x columns} apps.
 */
public class OneUiPagedAllAppsView extends PagedView<PageIndicatorDots> {

    /** Notified when the visible page changes. */
    public interface OnActivePageChangedListener {
        void onActivePageChanged(@Nullable AllAppsRecyclerView recyclerView, int page);
    }

    /**
     * Notified when page recycler views are replaced, so the owner can keep icon-update
     * registrations (AllAppsStore icon containers) in sync.
     */
    public interface OnRecyclerViewsChangedListener {
        void onRecyclerViewsChanged(@NonNull List<AllAppsRecyclerView> removed,
                @NonNull List<AllAppsRecyclerView> added);
    }

    private static final int RECYCLED_VIEW_POOL_SIZE = 64;

    private final ActivityContext mActivityContext;
    private final LayoutInflater mLayoutInflater;
    private final ArrayList<AppInfo> mApps = new ArrayList<>();
    private final ArrayList<AllAppsRecyclerView> mPageRecyclerViews = new ArrayList<>();
    private final Rect mPagePadding = new Rect();

    private final RecyclerView.RecycledViewPool mSharedPool = new RecyclerView.RecycledViewPool();

    @Nullable private OnActivePageChangedListener mOnActivePageChangedListener;
    @Nullable private OnRecyclerViewsChangedListener mOnRecyclerViewsChangedListener;
    private int mLastItemsPerPage = -1;
    private int mLastSpanCount = -1;
    private int mLastCellHeight = -1;
    private boolean mAppsDirty = true;

    private boolean mLayersEnabled = false;

    private final Runnable mRebuildRunnable = () -> rebuildPages(true);

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
            mPageIndicator.setMarkersCount(Math.max(1, getChildCount()));
            mPageIndicator.setActiveMarker(getNextPage());
        }
    }

    public void setOnActivePageChangedListener(@Nullable OnActivePageChangedListener listener) {
        mOnActivePageChangedListener = listener;
    }

    public void setOnRecyclerViewsChangedListener(
            @Nullable OnRecyclerViewsChangedListener listener) {
        mOnRecyclerViewsChangedListener = listener;
    }

    /**
     * Replaces the app list. Pages are rebuilt (keeping the current page when possible) once the
     * view has a size.
     */
    public void setApps(@NonNull List<AppInfo> apps) {
        if (mApps.equals(apps) && !mPageRecyclerViews.isEmpty()) {
            // Same apps in same order: only rebind so icon/label/badge changes are picked up.
            for (AllAppsRecyclerView rv : mPageRecyclerViews) {
                RecyclerView.Adapter<?> adapter = rv.getAdapter();
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
            // Grid shape (columns/rows) may still have changed, e.g. after a profile change.
            if (getMeasuredWidth() > 0 && getMeasuredHeight() > 0) {
                rebuildPages(true);
            }
            return;
        }
        mApps.clear();
        mApps.addAll(apps);
        mAppsDirty = true;

        removeCallbacks(mRebuildRunnable);
        if (getMeasuredWidth() > 0 && getMeasuredHeight() > 0) {
            rebuildPages(true);
        } else {
            post(mRebuildRunnable);
        }
    }

    public void setPagePadding(@NonNull Rect padding) {
        if (mPagePadding.equals(padding)) {
            return;
        }
        boolean verticalChanged = mPagePadding.top != padding.top
                || mPagePadding.bottom != padding.bottom;
        mPagePadding.set(padding);
        applyPaddingToPages();
        if (verticalChanged && !mPageRecyclerViews.isEmpty()) {
            // Cell height depends on the available vertical space.
            removeCallbacks(mRebuildRunnable);
            post(mRebuildRunnable);
        }
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

    /** Jumps back to the first page without animation. */
    public void resetToFirstPage() {
        if (getChildCount() > 0 && getNextPage() != 0) {
            setCurrentPage(0);
            if (mPageIndicator != null) {
                mPageIndicator.setActiveMarker(0);
            }
            dispatchActivePageChanged();
        }
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
        boolean isLargeScreen =
                mActivityContext.getDeviceProfile().getDeviceProperties().isLargeScreen();
        if (isLargeScreen && (mPagePadding.left > 0 || mPagePadding.right > 0)) {
            // Keep neighbouring pages from bleeding into the side margins on tablets.
            int saveCount = canvas.save();
            canvas.clipRect(
                    getScrollX() + mPagePadding.left,
                    getScrollY(),
                    getScrollX() + getWidth() - mPagePadding.right,
                    getScrollY() + getHeight());
            super.dispatchDraw(canvas);
            canvas.restoreToCount(saveCount);
        } else {
            super.dispatchDraw(canvas);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) {
            removeCallbacks(mRebuildRunnable);
            post(mRebuildRunnable);
        }
    }

    @Override
    protected boolean canScroll(float absVScroll, float absHScroll) {
        // Only claim clearly horizontal drags; vertical ones close the drawer.
        return absHScroll > absVScroll && super.canScroll(absVScroll, absHScroll);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(mRebuildRunnable);
    }

    private void rebuildPages(boolean preservePage) {
        int itemsPerPage = getItemsPerPage();
        if (itemsPerPage <= 0) {
            return;
        }
        int spanCount = getColumns();
        int adjustedCellHeight = getAdjustedCellHeight();

        if (!mAppsDirty
                && itemsPerPage == mLastItemsPerPage
                && spanCount == mLastSpanCount
                && adjustedCellHeight == mLastCellHeight
                && !mPageRecyclerViews.isEmpty()) {
            applyPaddingToPages();
            return;
        }
        mAppsDirty = false;
        mLastItemsPerPage = itemsPerPage;
        mLastSpanCount = spanCount;
        mLastCellHeight = adjustedCellHeight;

        int pageToRestore = preservePage ? Math.max(0, getNextPage()) : 0;

        disableHardwareLayers();

        List<AllAppsRecyclerView> removed = new ArrayList<>(mPageRecyclerViews);
        removeAllViews();
        mPageRecyclerViews.clear();

        int pageCount = Math.max(1, (int) Math.ceil(mApps.size() / (float) itemsPerPage));
        for (int page = 0; page < pageCount; page++) {
            int start = page * itemsPerPage;
            int end = Math.min(start + itemsPerPage, mApps.size());
            List<AppInfo> pageApps = start < end
                    ? new ArrayList<>(mApps.subList(start, end))
                    : new ArrayList<>();
            AllAppsRecyclerView recyclerView = createPageRecyclerView(spanCount, itemsPerPage,
                    adjustedCellHeight, pageApps);
            mPageRecyclerViews.add(recyclerView);
            addView(recyclerView);
        }

        applyPaddingToPages();
        setCurrentPage(Math.min(pageToRestore, Math.max(0, getChildCount() - 1)));
        if (mPageIndicator != null) {
            mPageIndicator.setMarkersCount(getChildCount());
            mPageIndicator.setActiveMarker(getNextPage());
        }
        if (mOnRecyclerViewsChangedListener != null) {
            mOnRecyclerViewsChangedListener.onRecyclerViewsChanged(removed,
                    Collections.unmodifiableList(mPageRecyclerViews));
        }
        dispatchActivePageChanged();
    }

    private AllAppsRecyclerView createPageRecyclerView(int spanCount, int itemsPerPage,
            int cellHeight, List<AppInfo> pageApps) {
        AllAppsRecyclerView recyclerView = new AllAppsRecyclerView(getContext()) {
            @Override
            public void scrollToTop() {
                // Pages never scroll; avoid touching the (unbound) fast scroller.
                RecyclerView.LayoutManager layoutManager = getLayoutManager();
                if (layoutManager instanceof GridLayoutManager glm) {
                    glm.scrollToPositionWithOffset(0, 0);
                }
            }

            @Override
            public boolean shouldContainerScroll(MotionEvent ev, View eventSource) {
                // Pages can't scroll vertically, so vertical drags always belong to the container.
                return true;
            }

            @Override
            public boolean supportsFastScrolling() {
                return false;
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
        if (mPageIndicator != null) {
            mPageIndicator.setActiveMarker(getNextPage());
        }
        if (mOnActivePageChangedListener != null) {
            mOnActivePageChangedListener.onActivePageChanged(getCurrentRecyclerView(),
                    getNextPage());
        }
    }

    private int getColumns() {
        return Math.max(1,
                mActivityContext.getDeviceProfile().getAllAppsProfile()
                        .getNumShownAllAppsColumns());
    }

    private int getRowsPerPage() {
        int rows = Math.max(1, mActivityContext.getDeviceProfile().inv.numRows);
        // Never squeeze cells below the regular all apps cell height (e.g. landscape).
        int available = getMeasuredHeight() - mPagePadding.top - mPagePadding.bottom;
        int minCell = mActivityContext.getDeviceProfile().getAllAppsProfile().getCellHeightPx();
        if (available > 0 && minCell > 0) {
            rows = Math.max(1, Math.min(rows, available / minCell));
        }
        return rows;
    }

    private int getAdjustedCellHeight() {
        int baseCellHeight = Math.max(1,
                mActivityContext.getDeviceProfile().getAllAppsProfile().getCellHeightPx());
        int height = getMeasuredHeight();
        if (height <= 0) {
            return baseCellHeight;
        }
        int availableHeight = Math.max(0, height - mPagePadding.top - mPagePadding.bottom);
        return Math.max(1, availableHeight / getRowsPerPage());
    }

    private int getItemsPerPage() {
        if (getMeasuredWidth() <= 0 || getMeasuredHeight() <= 0) {
            return 0;
        }
        return getRowsPerPage() * getColumns();
    }

    private class PageAdapter extends RecyclerView.Adapter<PageAdapter.IconHolder> {
        private final List<AppInfo> mPageApps;
        private final int mCellHeight;
        private final int mTextColor;
        private final int mLayoutRes;
        private final View.OnClickListener mClickListener;
        private final View.OnLongClickListener mLongClickListener;
        private final CustomActionsListener mCustomActionsListener;

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
            mCustomActionsListener = mActivityContext.getAllAppsItemCustomActionsListener();
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
            icon.setCustomActionsListener(mCustomActionsListener);
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
