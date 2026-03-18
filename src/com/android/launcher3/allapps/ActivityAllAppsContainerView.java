/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.launcher3.Flags.enableExpandingPauseWorkButton;
import static com.android.launcher3.LauncherModel.useModelRepositoryBinding;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.MAIN;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.SEARCH;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.WORK;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_PRIVATE_SPACE_HEADER;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_WORK_DISABLED_CARD;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_WORK_EDU_CARD;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_COUNT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_TAP_ON_PERSONAL_TAB;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_TAP_ON_WORK_TAB;
import static com.android.launcher3.util.ScrollableLayoutManager.PREDICTIVE_BACK_MIN_SCALE;
import static com.android.launcher3.views.RecyclerViewFastScroller.FastScrollerLocation.ALL_APPS_SCROLLER;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Process;
import android.os.UserManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.VisibleForTesting;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.ColorUtils;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Flags;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.allapps.search.AllAppsSearchUiDelegate;
import com.android.launcher3.allapps.search.SearchAdapterProvider;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.keyboard.FocusedItemDecorator;
import com.android.launcher3.keyboard.ViewGroupFocusHelper;
import com.android.launcher3.model.StringCache;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.repository.StringCacheRepository;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.recyclerview.AllAppsRecyclerViewPool;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.ViewEx;
import com.android.launcher3.pageindicators.PageIndicatorDots;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.views.RecyclerViewFastScroller;
import com.android.launcher3.views.ScrimView;
import com.android.launcher3.views.SpringRelativeLayout;
import com.android.launcher3.workprofile.PersonalWorkSlidingTabStrip;
import com.android.systemui.plugins.AllAppsRow;

import kotlin.Unit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * All apps container view with search support for use in a dragging activity.
 *
 * @param <T> Type of context inflating all apps.
 */
public class ActivityAllAppsContainerView<T extends Context & ActivityContext>
        extends SpringRelativeLayout implements DragSource, Insettable,
        OnDeviceProfileChangeListener, PersonalWorkSlidingTabStrip.OnActivePageChangedListener,
        ScrimView.ScrimDrawingController {


    private static final String TAG = "ActivityAllAppsContainerView";
    public static final float PULL_MULTIPLIER = .02f;
    public static final float FLING_VELOCITY_MULTIPLIER = 1200f;
    protected static final String BUNDLE_KEY_CURRENT_PAGE = "launcher.allapps.current_page";
    // As of this writing, search transition does not seem to work properly, so set duration to 0.
    private static final long DEFAULT_SEARCH_TRANSITION_DURATION_MS = 0;
    // Render the header protection at all times to debug clipping issues.
    private static final boolean DEBUG_HEADER_PROTECTION = false;
    private static final String SEARCH_PLACEMENT_HIDDEN = "hidden";
    private static final String SEARCH_PLACEMENT_BOTTOM = "bottom";
    /** Context of an activity or window that is inflating this container. */

    protected final T mActivityContext;
    protected final List<AdapterHolder> mAH;
    protected final Predicate<ItemInfo> mPersonalMatcher;
    protected WorkProfileManager mWorkManager;
    protected final PrivateProfileManager mPrivateProfileManager;
    protected final Point mFastScrollerOffset = new Point();
    protected final float mHeaderThreshold;
    protected final AllAppsSearchUiDelegate mSearchUiDelegate;

    // Used to animate Search results out and A-Z apps in, or vice-versa.
    private final SearchTransitionController mSearchTransitionController;
    private final Paint mHeaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect mInsets = new Rect();
    private final AllAppsStore mAllAppsStore;
    private final RecyclerView.OnScrollListener mScrollListener =
            new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    updateHeaderScroll(recyclerView.computeVerticalScrollOffset());
                }
            };
    private final Paint mNavBarScrimPaint;
    private final int mHeaderProtectionColor;
    private final int mPrivateSpaceBottomExtraSpace;
    private final Path mTmpPath = new Path();
    private final RectF mTmpRectF = new RectF();
    protected AllAppsPagedView mViewPager;
    protected FloatingHeaderView mHeader;
    protected final List<AllAppsRow> mAdditionalHeaderRows = new ArrayList<>();
    protected View mBottomSheetBackground;
    protected RecyclerViewFastScroller mFastScroller;
    private ConstraintLayout mFastScrollLetterLayout;

    /**
     * View that defines the search box. Result is rendered inside {@link #mSearchRecyclerView}.
     */
    protected View mSearchContainer;
    protected SearchUiManager mSearchUiManager;
    protected boolean mUsingTabs;
    protected RecyclerViewFastScroller mTouchHandler;

    /** {@code true} when rendered view is in search state instead of the scroll state. */
    private boolean mIsSearching;
    private boolean mShowFastScroller;
    /** One of {@link AppDrawerStyle}; always NORMAL outside of the Launcher activity. */
    private String mAppDrawerStyle = AppDrawerStyle.NORMAL;
    @Nullable private PageIndicatorDots mOneUiPageIndicator;
    @Nullable private OneUiPagedAllAppsView mOneUiPagedView;
    private final Runnable mRefreshOneUiAppsRunnable = this::refreshOneUiApps;
    private boolean mRebindAdaptersAfterSearchAnimation;
    private int mNavBarScrimHeight = 0;
    private int mImeInsetBottom = 0;
    private SearchRecyclerView mSearchRecyclerView;
    protected SearchAdapterProvider<?> mMainAdapterProvider;
    private View mBottomSheetHandleArea;
    private boolean mHasWorkApps;
    private boolean mHasPrivateApps;
    private float[] mBottomSheetCornerRadii;
    @Nullable private float[] mFullCornerRadii;
    @Nullable private RecyclerView.RecycledViewPool mListStyleViewPool;
    private ScrimView mScrimView;
    private int mHeaderColor;
    private int mBottomSheetBackgroundColorBlurFallback;
    private int mBottomSheetBackgroundColorOverBlur;
    /** Custom drawer color with opacity applied; used when {@link #mUseCustomBackgroundColor}. */
    private boolean mUseCustomBackgroundColor;
    private int mCustomBackgroundColor = Color.TRANSPARENT;
    private int mTabsProtectionAlpha;
    @Nullable private AllAppsTransitionController mAllAppsTransitionController;

    @Nullable private String mSearchPlacement;

    private final View.OnLayoutChangeListener mSearchContainerLayoutListener =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if ((bottom - top) != (oldBottom - oldTop)) {
                    updateFastScrollerBottomMargin();
                }
            };

    public ActivityAllAppsContainerView(Context context) {
        this(context, null);
    }

    public ActivityAllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActivityAllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivityContext = ActivityContext.lookupContext(context);
        UserManager userManager = mActivityContext.getSystemService(UserManager.class);
        mPersonalMatcher = ItemInfoMatcher.ofCurrentOrDualUser(userManager, Process.myUserHandle());
        mAllAppsStore = mActivityContext.getActivityComponent().getAppsStore();

        mHeaderThreshold = getResources().getDimensionPixelSize(
                R.dimen.dynamic_grid_cell_border_spacing);
        // With a custom drawer color, protect the header with that color instead of the theme's.
        mHeaderProtectionColor = AppDrawerStyle.isCustomColorEnabled(context)
                ? AppDrawerStyle.getCustomBackgroundColor(context)
                : Themes.getAttrColor(context, R.attr.allappsHeaderProtectionColor);

        mWorkManager = new WorkProfileManager(
                this,
                mActivityContext.getStatsLogManager(),
                UserCache.INSTANCE.get(mActivityContext));
        mPrivateProfileManager = new PrivateProfileManager(
                this,
                mActivityContext.getUiExecutor(),
                mActivityContext.getStatsLogManager(),
                UserCache.INSTANCE.get(mActivityContext));
        mPrivateSpaceBottomExtraSpace = context.getResources().getDimensionPixelSize(
                R.dimen.ps_extra_bottom_padding);
        mAH = Arrays.asList(null, null, null);
        mNavBarScrimPaint = new Paint();
        mNavBarScrimPaint.setColor(Themes.getNavBarScrimColor(mActivityContext));

        AllAppsStore.OnUpdateListener onAppsUpdated = this::onAppsUpdated;
        mAllAppsStore.addUpdateListener(onAppsUpdated);
        initProfileStatus();
        // This is a focus listener that proxies focus from a view into the list view.  This is to
        // work around the search box from getting first focus and showing the cursor.
        setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && getActiveRecyclerView() != null) {
                getActiveRecyclerView().requestFocus();
            }
        });
        mSearchUiDelegate = createSearchUiDelegate();
        updateAppDrawerStyle();
        initContent();

        mSearchTransitionController = new SearchTransitionController(this);
    }

    /** Creates the delegate for initializing search. */
    protected AllAppsSearchUiDelegate createSearchUiDelegate() {
        return new AllAppsSearchUiDelegate(this);
    }

    public AllAppsSearchUiDelegate getSearchUiDelegate() {
        return mSearchUiDelegate;
    }

    /**
     * Initializes the view hierarchy and internal variables. Any initialization which actually uses
     * these members should be done in {@link #onFinishInflate()}.
     * In terms of subclass initialization, the following would be parallel order for activity:
     *   initContent -> onPreCreate
     *   constructor/init -> onCreate
     *   onFinishInflate -> onPostCreate
     */
    protected void initContent() {
        mShowFastScroller = shouldShowFastScroller();
        mSearchPlacement = LauncherPrefs.ALL_APPS_SEARCH_PLACEMENT.get(getContext());
        mMainAdapterProvider = mSearchUiDelegate.createMainAdapterProvider();

        mAH.set(AdapterHolder.MAIN, new AdapterHolder(AdapterHolder.MAIN,
                new AlphabeticalAppsList(mActivityContext,
                        mAllAppsStore,
                        null,
                        mPrivateProfileManager)));
        mAH.set(AdapterHolder.WORK, new AdapterHolder(AdapterHolder.WORK,
                new AlphabeticalAppsList(mActivityContext, mAllAppsStore, mWorkManager, null)));
        mAH.set(SEARCH, new AdapterHolder(SEARCH,
                new AlphabeticalAppsList(mActivityContext, null, null, null)));

        getLayoutInflater().inflate(R.layout.all_apps_content, this);
        mHeader = findViewById(R.id.all_apps_header);
        mAdditionalHeaderRows.clear();
        mAdditionalHeaderRows.addAll(getAdditionalHeaderRows());
        mBottomSheetBackground = findViewById(R.id.bottom_sheet_background);
        mBottomSheetHandleArea = findViewById(R.id.bottom_sheet_handle_area);
        mSearchRecyclerView = findViewById(R.id.search_results_list_view);
        mFastScroller = findViewById(R.id.fast_scroller);
        mFastScroller.setPopupView(findViewById(R.id.fast_scroller_popup));
        mFastScrollLetterLayout = findViewById(R.id.scroll_letter_layout);
        mOneUiPageIndicator = findViewById(R.id.all_apps_page_indicator);
        if (mOneUiPageIndicator != null) {
            mOneUiPageIndicator.setVisibility(GONE);
        }
        mFastScroller.setVisibility(mShowFastScroller ? VISIBLE : INVISIBLE);
        mFastScrollLetterLayout.setVisibility(mShowFastScroller ? VISIBLE : INVISIBLE);
        setClipChildren(false);

        mSearchContainer = inflateSearchBar();
        if (!isSearchBarFloating()) {
            // Add the search box above everything else in this container (if the flag is enabled,
            // it's added to drag layer in onAttach instead).
            addView(mSearchContainer);
            // The search container is visually at the top of the all apps UI, and should thus be
            // focused by default. It's added to end of the children list, so it needs to be
            // explicitly marked as focused by default.
            mSearchContainer.setFocusedByDefault(true);
        }
        mSearchUiManager = (SearchUiManager) mSearchContainer;
        mSearchContainer.addOnLayoutChangeListener(mSearchContainerLayoutListener);
    }

    public List<AllAppsRow> getAdditionalHeaderRows() {
        return List.of();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSearchContainer.setVisibility(isSearchBarHidden() ? View.GONE : View.VISIBLE);

        mAH.get(SEARCH).setup(mSearchRecyclerView,
                /* Filter out A-Z apps */ itemInfo -> false);
        rebindAdapters(true /* force */);
        float cornerRadius = Themes.getDialogCornerRadius(getContext());
        mBottomSheetCornerRadii = new float[]{
                cornerRadius,
                cornerRadius, // Top left radius in px
                cornerRadius,
                cornerRadius, // Top right radius in px
                0,
                0, // Bottom right
                0,
                0 // Bottom left
        };

        int layerFg = getContext().getColor(R.color.blur_shade_panel_fg);
        int layerBg = getContext().getColor(R.color.blur_shade_panel_bg);
        mBottomSheetBackgroundColorOverBlur = ColorUtils.compositeColors(layerFg, layerBg);
        mBottomSheetBackgroundColorBlurFallback = getContext().getColor(
                Utilities.isDarkTheme(getContext()) ? android.R.color.system_accent2_800
                        : android.R.color.system_accent2_200);
        mUseCustomBackgroundColor = AppDrawerStyle.isCustomColorEnabled(getContext());
        mCustomBackgroundColor = mUseCustomBackgroundColor
                ? AppDrawerStyle.getCustomBackgroundColorWithOpacity(getContext())
                : Color.TRANSPARENT;

        mSearchUiManager.initializeSearch(this);
        if (useModelRepositoryBinding()) {
            ViewEx.registerLifecycleTask(this,
                    () -> StringCacheRepository.getStringCache(getContext())
                            .forEach(mActivityContext.getUiExecutor(), c -> updateWorkUI()));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isSearchBarFloating()) {
            // Note: for Taskbar this is removed in TaskbarAllAppsController#cleanUpOverlay when the
            // panel is closed. Can't do so in onDetach because we are also a child of drag layer
            // so can't remove its views during that dispatch.
            mActivityContext.getDragLayer().addView(mSearchContainer);
            mSearchUiDelegate.onInitializeSearchBar();
        }
        mActivityContext.addOnDeviceProfileChangeListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mActivityContext.removeOnDeviceProfileChangeListener(this);
    }

    public SearchUiManager getSearchUiManager() {
        return mSearchUiManager;
    }

    public View getSearchView() {
        return mSearchContainer;
    }

    /** Invoke when the current search session is finished. */
    public void onClearSearchResult() {
        getMainAdapterProvider().clearHighlightedItem();
        animateToSearchState(false);
        rebindAdapters();
    }

    /**
     * Sets results list for search
     */
    public void setSearchResults(ArrayList<AdapterItem> results) {
        getMainAdapterProvider().clearHighlightedItem();
        if (getSearchResultList().setSearchResults(results)) {
            getSearchRecyclerView().onSearchResultsChanged();
        }
        if (results != null) {
            animateToSearchState(true);
        }
    }

    /**
     * Sets results list for search.
     *
     * @param searchResultCode indicates if the result is final or intermediate for a given query
     *                         since we can get search results from multiple sources.
     */
    public void setSearchResults(ArrayList<AdapterItem> results, int searchResultCode) {
        setSearchResults(results);
        mSearchUiDelegate.onSearchResultsChanged(results, searchResultCode);
    }

    private void animateToSearchState(boolean goingToSearch) {
        animateToSearchState(goingToSearch, DEFAULT_SEARCH_TRANSITION_DURATION_MS);
    }

    public void setAllAppsTransitionController(
            AllAppsTransitionController allAppsTransitionController) {
        mAllAppsTransitionController = allAppsTransitionController;
    }

    void animateToSearchState(boolean goingToSearch, long durationMs) {
        if (!mSearchTransitionController.isRunning() && goingToSearch == isSearching()) {
            return;
        }
        mFastScroller.setVisibility(goingToSearch || !mShowFastScroller ? INVISIBLE : VISIBLE);
        if (goingToSearch) {
            // Fade out the button to pause work apps.
            mWorkManager.onActivePageChanged(SEARCH);
        } else if (mAllAppsTransitionController != null) {
            // If exiting search, revert predictive back scale on all apps
            mAllAppsTransitionController.animateAllAppsToNoScale();
            mFastScroller.setVisibility(mShowFastScroller ? VISIBLE : INVISIBLE);
            mFastScrollLetterLayout.setVisibility(mShowFastScroller ? VISIBLE : INVISIBLE);
        }
        setScrollbarVisibility(!goingToSearch);
        mSearchTransitionController.animateToState(goingToSearch, durationMs,
                /* onEndRunnable = */ () -> {
                    mIsSearching = goingToSearch;
                    updateSearchResultsVisibility();
                    int previousPage = getCurrentPage();
                    if (mRebindAdaptersAfterSearchAnimation) {
                        rebindAdapters(false);
                        mRebindAdaptersAfterSearchAnimation = false;
                    }

                    if (goingToSearch) {
                        mSearchUiDelegate.onAnimateToSearchStateCompleted();
                    } else {
                        setSearchResults(null);
                        if (mViewPager != null) {
                            mViewPager.setCurrentPage(previousPage);
                        }
                        onActivePageChanged(previousPage);
                    }
                });
    }

    public boolean shouldContainerScroll(MotionEvent ev) {
        BaseDragLayer dragLayer = mActivityContext.getDragLayer();
        // IF the MotionEvent is inside the search box or handle area, and the container keeps on
        // receiving touch input, container should move down.
        if (dragLayer.isEventOverView(mSearchContainer, ev)
                || dragLayer.isEventOverView(mBottomSheetHandleArea, ev)) {
            return true;
        }
        if (mOneUiPagedView != null && !isSearching()) {
            // Pages never scroll vertically; vertical drags always move the container.
            return true;
        }
        AllAppsRecyclerView rv = getActiveRecyclerView();
        if (rv == null || rv.getParent() == null || rv.getWindowId() == null) {
            return true;
        }
        if (rv.getScrollbar() != null
                && rv.getScrollbar().getThumbOffsetY() >= 0
                && dragLayer.isEventOverView(rv.getScrollbar(), ev)) {
            return false;
        }
        // Scroll if not within the container view (e.g. over large-screen scrim).
        View visibleContainer = getVisibleContainerView();
        if (visibleContainer == null || visibleContainer.getWindowId() == null
                || !dragLayer.isEventOverView(visibleContainer, ev)) {
            return true;
        }
        return rv.shouldContainerScroll(ev, dragLayer);
    }

    /**
     * Resets the UI to be ready for fresh interactions in the future. Exits search and returns to
     * A-Z apps list.
     *
     * @param animate Whether to animate the header during the reset (e.g. switching profile tabs).
     * @param clearScrim Scrim gets set during AllAppsTransitionController and should only be
     *                  {@code true} if All Apps is not visible.
     */
    public void reset(boolean animate, boolean clearScrim) {
        reset(animate, true, clearScrim);
    }

    /**
     * Resets the UI to be ready for fresh interactions in the future.
     *
     * @param animate Whether to animate the header during the reset (e.g. switching profile tabs).
     * @param exitSearch Whether to force exit the search state and return to A-Z apps list.
     * @param clearScrim Whether to clear the all apps scrim.
     */
    public void reset(boolean animate, boolean exitSearch, boolean clearScrim) {
        // Scroll Main and Work RV to top. Search RV is done in `resetSearch`.
        for (int i = 0; i < mAH.size(); i++) {
            if (i != SEARCH && mAH.get(i).mRecyclerView != null) {
                mAH.get(i).mRecyclerView.scrollToTop();
            }
        }
        if (mOneUiPagedView != null) {
            mOneUiPagedView.resetToFirstPage();
        }
        if (mTouchHandler != null) {
            mTouchHandler.endFastScrolling();
        }
        if (mHeader != null && mHeader.getVisibility() == VISIBLE) {
            mHeader.reset(animate);
        }
        // Reset the base recycler view after transitioning home.
        updateHeaderScroll(0);
        if (exitSearch) {
            // Reset the search bar and search RV after transitioning home.
            mActivityContext.getUiExecutor().getHandler().post(mSearchUiManager::resetSearch);
        }
        if (isSearching()) {
            mWorkManager.reset();
        }
        if (mScrimView != null && clearScrim) {
            mScrimView.setDrawingController(null);
        }
    }

    /**
     * Exits search and returns to A-Z apps list. Scroll to the private space header.
     */
    public void resetAndScrollToPrivateSpaceHeader() {
        // Animate to A-Z with 0 time to reset the animation with proper state management.
        // We can't rely on `animateToSearchState` with delay inside `resetSearch` because that will
        // conflict with following scrolling to bottom, so we need it with 0 time here.
        animateToSearchState(false, 0);

        mActivityContext.getUiExecutor().getHandler().post(() -> {
            // Reset the search bar after transitioning home.
            // When `resetSearch` is called after `animateToSearchState` is finished, the inside
            // `animateToSearchState` with delay is a just no-op and return early.
            mSearchUiManager.resetSearch();
            // Switch to the main tab
            switchToTab(ActivityAllAppsContainerView.AdapterHolder.MAIN);
            // Scroll to bottom
            if (mPrivateProfileManager != null) {
                mPrivateProfileManager.scrollForHeaderToBeVisibleInContainer(
                        getActiveAppsRecyclerView(),
                        getPersonalAppList().getAdapterItems(),
                        mPrivateProfileManager.getPsHeaderHeight(),
                        mActivityContext.getDeviceProfile().getAllAppsProfile().getCellHeightPx());
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        mSearchUiManager.preDispatchKeyEvent(event);
        return super.dispatchKeyEvent(event);
    }

    public String getDescription() {
        if (!mUsingTabs && isSearching()) {
            return getContext().getString(R.string.all_apps_search_results);
        } else {
            StringCache cache = mActivityContext.getStringCache();
            if (mUsingTabs) {
                if (cache != null) {
                    return isPersonalTab()
                            ? cache.allAppsPersonalTabAccessibility
                            : cache.allAppsWorkTabAccessibility;
                } else {
                    return isPersonalTab()
                            ? getContext().getString(R.string.all_apps_button_personal_label)
                            : getContext().getString(R.string.all_apps_button_work_label);
                }
            }
            return getContext().getString(R.string.all_apps_button_label);
        }
    }

    public boolean isSearching() {
        return mIsSearching;
    }

    /**
     * @return {@code true} if back gesture should exit search rather than change launcher state.
      */
    public boolean shouldBackExitSearch() {
        return isSearching();
    }

    @Override
    public void onActivePageChanged(int currentActivePage) {
        if (mSearchTransitionController.isRunning()) {
            // Will be called at the end of the animation.
            return;
        }
        if (mAH.get(currentActivePage).mRecyclerView != null) {
            mAH.get(currentActivePage).mRecyclerView.bindFastScrollbar(mFastScroller,
                    ALL_APPS_SCROLLER);
        }
        // Header keeps track of active recycler view to properly render header protection.
        mHeader.setActiveRV(currentActivePage);

        mWorkManager.onActivePageChanged(currentActivePage);
    }

    protected void rebindAdapters() {
        rebindAdapters(false /* force */);
    }

    protected void rebindAdapters(boolean force) {
        Log.d(TAG, "rebindAdapters: force: " + force);
        String previousStyle = mAppDrawerStyle;
        updateAppDrawerStyle();
        if (!previousStyle.equals(mAppDrawerStyle)) {
            force = true;
        }
        if (mSearchTransitionController.isRunning()) {
            mRebindAdaptersAfterSearchAnimation = true;
            return;
        }
        updateSearchResultsVisibility();

        boolean showTabs = shouldShowTabs();
        if (showTabs == mUsingTabs && !force) {
            if (mOneUiPagedView != null) {
                // The app list changed; repaginate once the A-Z list has been refreshed.
                removeCallbacks(mRefreshOneUiAppsRunnable);
                post(mRefreshOneUiAppsRunnable);
            }
            Log.d(TAG, "rebindAdapters: Not needed.");
            return;
        }

        // replaceAppsRVcontainer() needs to use both mUsingTabs value to remove the old view AND
        // showTabs value to create new view. Hence the mUsingTabs new value assignment MUST happen
        // after this call.
        replaceAppsRVContainer(showTabs);
        mUsingTabs = showTabs;

        unregisterMainIconContainers();
        mAllAppsStore.unregisterIconContainer(mAH.get(AdapterHolder.WORK).mRecyclerView);
        mAllAppsStore.unregisterIconContainer(mAH.get(AdapterHolder.SEARCH).mRecyclerView);

        final AllAppsRecyclerView mainRecyclerView;
        final AllAppsRecyclerView workRecyclerView;
        if (mUsingTabs) {
            mainRecyclerView = (AllAppsRecyclerView) mViewPager.getChildAt(0);
            workRecyclerView = (AllAppsRecyclerView) mViewPager.getChildAt(1);
            mAH.get(AdapterHolder.MAIN).setup(mainRecyclerView, mPersonalMatcher);
            mAH.get(AdapterHolder.WORK).setup(workRecyclerView, mWorkManager.getItemInfoMatcher());
            workRecyclerView.setId(R.id.apps_list_view_work);
            if (enableExpandingPauseWorkButton()
                    || FeatureFlags.ENABLE_EXPANDING_PAUSE_WORK_BUTTON.get()) {
                mAH.get(AdapterHolder.WORK).mRecyclerView.addOnScrollListener(
                        mWorkManager.newScrollListener());
            }
            mViewPager.getPageIndicator().setActiveMarker(AdapterHolder.MAIN);
            findViewById(R.id.tab_personal)
                    .setOnClickListener((View view) -> {
                        Log.d(TAG, "rebindAdapters: " + "Clicked personal tab.");
                        if (mViewPager.snapToPage(AdapterHolder.MAIN)) {
                            mActivityContext.getStatsLogManager().logger()
                                    .log(LAUNCHER_ALLAPPS_TAP_ON_PERSONAL_TAB);
                        }
                    });
            findViewById(R.id.tab_work)
                    .setOnClickListener((View view) -> {
                        Log.d(TAG, "rebindAdapters: " + "Clicked work tab.");
                        if (mViewPager.snapToPage(AdapterHolder.WORK)) {
                            mActivityContext.getStatsLogManager().logger()
                                    .log(LAUNCHER_ALLAPPS_TAP_ON_WORK_TAB);
                        }
                    });
            setDeviceManagementResources();
            if (mHeader.isSetUp()) {
                onActivePageChanged(mViewPager.getNextPage());
            }
        } else if (mOneUiPagedView != null) {
            mainRecyclerView = null;
            workRecyclerView = null;
            mAH.get(AdapterHolder.WORK).mRecyclerView = null;
            // No tabs in the paged style: show personal and work apps together.
            mAH.get(AdapterHolder.MAIN).mAppsList.updateItemFilter(
                    mPersonalMatcher.or(mWorkManager.getItemInfoMatcher()));
            bindOneUiPagedView();
        } else {
            mainRecyclerView = findViewById(R.id.apps_list_view);
            workRecyclerView = null;
            mAH.get(AdapterHolder.MAIN).setup(mainRecyclerView, mPersonalMatcher);
            mAH.get(AdapterHolder.WORK).mRecyclerView = null;
        }
        if (mainRecyclerView != null) {
            if (AppDrawerStyle.isHorizontalList(mAppDrawerStyle)) {
                // The shared pool is pre-inflated with stock grid icons (icon above label).
                // Recycling those into the list would show a centered single-column grid, so
                // the list style gets its own pool.
                if (mListStyleViewPool == null) {
                    mListStyleViewPool = new RecyclerView.RecycledViewPool();
                }
                mainRecyclerView.setRecycledViewPool(mListStyleViewPool);
                if (workRecyclerView != null) {
                    workRecyclerView.setRecycledViewPool(mListStyleViewPool);
                }
                mainRecyclerView.updatePoolSize();
            } else {
                setUpCustomRecyclerViewPool(
                        mainRecyclerView,
                        workRecyclerView,
                        mActivityContext.getActivityComponent().getSharedAppsPool());
            }
        }
        mShowFastScroller = shouldShowFastScroller();
        if (!isSearching()) {
            mFastScroller.setVisibility(mShowFastScroller ? VISIBLE : INVISIBLE);
            mFastScrollLetterLayout.setVisibility(mShowFastScroller ? VISIBLE : INVISIBLE);
        }
        setupHeader();

        updateFastScrollerBottomMargin();

        if (mOneUiPagedView == null) {
            // Paged recycler views are registered as pages get (re)built.
            mAllAppsStore.registerIconContainer(mAH.get(AdapterHolder.MAIN).mRecyclerView);
        }
        mAllAppsStore.registerIconContainer(mAH.get(AdapterHolder.WORK).mRecyclerView);
        mAllAppsStore.registerIconContainer(mAH.get(AdapterHolder.SEARCH).mRecyclerView);
    }

    /**
     * Wire custom {@link RecyclerView.RecycledViewPool} to main and work
     * {@link AllAppsRecyclerView}.
     *
     * Also update max pool size. This is because all apps rv's hidden visibility is changed to
     * {@link View#GONE} from {@link View#INVISIBLE}, thus we cannot rely on layout pass to update
     * pool size.
     */
    private static void setUpCustomRecyclerViewPool(
            @NonNull AllAppsRecyclerView mainRecyclerView,
            @Nullable AllAppsRecyclerView workRecyclerView,
            @NonNull AllAppsRecyclerViewPool recycledViewPool) {
        mainRecyclerView.setRecycledViewPool(recycledViewPool);
        if (workRecyclerView != null) {
            workRecyclerView.setRecycledViewPool(recycledViewPool);
        }
        mainRecyclerView.updatePoolSize();
    }

    private void replaceAppsRVContainer(boolean showTabs) {
        Log.d(TAG, "replaceAppsRVContainer: showTabs: " + showTabs);
        for (int i = AdapterHolder.MAIN; i <= AdapterHolder.WORK; i++) {
            AdapterHolder adapterHolder = mAH.get(i);
            if (adapterHolder.mRecyclerView != null) {
                adapterHolder.mRecyclerView.setLayoutManager(null);
                adapterHolder.mRecyclerView.setAdapter(null);
            }
        }
        View oldView = getAppsRecyclerViewContainer();
        if (mOneUiPagedView != null) {
            for (AllAppsRecyclerView rv : mOneUiPagedView.getRecyclerViews()) {
                mAllAppsStore.unregisterIconContainer(rv);
            }
            removeCallbacks(mRefreshOneUiAppsRunnable);
            mOneUiPagedView.setOnActivePageChangedListener(null);
            mOneUiPagedView.setOnRecyclerViewsChangedListener(null);
            mOneUiPagedView = null;
        }
        int index = indexOfChild(oldView);
        removeView(oldView);
        final boolean paged = !showTabs && AppDrawerStyle.isVerticalPaged(mAppDrawerStyle);
        int layout = showTabs ? R.layout.all_apps_tabs
                : paged ? R.layout.all_apps_oneui_paged_layout
                : R.layout.all_apps_rv_layout;
        final View rvContainer = getLayoutInflater().inflate(layout, this, false);
        addView(rvContainer, index);
        if (showTabs) {
            mViewPager = (AllAppsPagedView) rvContainer;
            mViewPager.initParentViews(this);
            mViewPager.getPageIndicator().setOnActivePageChangedListener(this);
            mViewPager.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    @Px final int bottomOffsetPx =
                            (int) (ActivityAllAppsContainerView.this.getMeasuredHeight()
                                    * PREDICTIVE_BACK_MIN_SCALE);
                    outline.setRect(
                            0,
                            0,
                            view.getMeasuredWidth(),
                            view.getMeasuredHeight() + bottomOffsetPx);
                }
            });

            mWorkManager.reset();
            post(() -> mAH.get(AdapterHolder.WORK).applyPadding());
        } else {
            mWorkManager.detachWorkUtilityViews();
            mViewPager = null;
            if (paged) {
                mOneUiPagedView = (OneUiPagedAllAppsView) rvContainer;
            }
        }

        removeCustomRules(rvContainer);
        removeCustomRules(getSearchRecyclerView());
        if (isSearchBarHidden()) {
            mSearchContainer.setVisibility(View.GONE);
            alignParentTop(rvContainer, showTabs);
            alignParentTop(getSearchRecyclerView(), /* tabs= */ false);
        } else {
            mSearchContainer.setVisibility(View.VISIBLE);
            if (isSearchBarFloating()) {
                alignParentTop(rvContainer, showTabs);
                alignParentTop(getSearchRecyclerView(), /* tabs= */ false);
            } else if (isSearchBarAtBottom()) {
                layoutSearchContainerBottom();
                layoutAboveSearchContainer(rvContainer, showTabs);
                layoutAboveSearchContainer(getSearchRecyclerView(), /* tabs= */ false);
            } else {
                layoutSearchContainerTop();
                layoutBelowSearchContainer(rvContainer, showTabs);
                layoutBelowSearchContainer(getSearchRecyclerView(), /* tabs= */ false);
            }
        }

        if (mOneUiPagedView != null
                && rvContainer.getLayoutParams() instanceof RelativeLayout.LayoutParams rvLp) {
            // Keep the pages above the page indicator (which itself sits above the search bar
            // when the search bar is at the bottom).
            rvLp.addRule(RelativeLayout.ABOVE, R.id.all_apps_page_indicator);
            rvLp.bottomMargin = 0;
            rvContainer.setLayoutParams(rvLp);
        }
        updateOneUiPageIndicatorLayout();

        updateSearchResultsVisibility();
    }

    void setupHeader() {
        mAdditionalHeaderRows.forEach(row -> mHeader.onPluginDisconnected(row));

        mHeader.setVisibility(View.VISIBLE);
        boolean tabsHidden = !mUsingTabs;
        mHeader.setup(
                mAH.get(AdapterHolder.MAIN).mRecyclerView,
                mAH.get(AdapterHolder.WORK).mRecyclerView,
                (SearchRecyclerView) mAH.get(SEARCH).mRecyclerView,
                getCurrentPage(),
                tabsHidden);

        int padding = mHeader.getMaxTranslation();
        for (int i = 0; i < mAH.size(); i++) {
            final AdapterHolder adapterHolder = mAH.get(i);
            // Search and other adapters need to be handled a bit differently; otherwise, when
            // when leaving search, the All Apps view may be noticeably shifted downward because
            // its padding was unnecessarily impacted, and never restored, upon entering search.
            if (i != AdapterHolder.SEARCH && !tabsHidden && mHeader.getFloatingRowsHeight() == 0) {
                // Only the Search adapter needs padding when there are tabs but no floating rows.
                adapterHolder.mPadding.top = 0;
            } else {
                adapterHolder.mPadding.top = padding;
            }
            adapterHolder.applyPadding();
            if (adapterHolder.mRecyclerView != null) {
                adapterHolder.mRecyclerView.scrollToTop();
            }
        }
        mAdditionalHeaderRows.forEach(row -> mHeader.onPluginConnected(row, mActivityContext));

        removeCustomRules(mHeader);
        if (isSearchBarFloating() || isSearchBarHidden() || isSearchBarAtBottom()) {
            alignParentTop(mHeader, false /* includeTabsMargin */);
        } else {
            layoutBelowSearchContainer(mHeader, false /* includeTabsMargin */);
        }
        applyOneUiPagePadding();
    }

    /**
     * Force header height update with an offset. Used by SearchView to
     * request {@link FloatingHeaderView} to update its maxTranslation for multiline search bar.
     */
    public void forceUpdateHeaderHeight(int offset) {
        mHeader.updateSearchBarOffset(offset);
    }

    @Override
    public void addChildrenForAccessibility(ArrayList<View> arrayList) {
        super.addChildrenForAccessibility(arrayList);
        if (!Flags.floatingSearchBar()) {
            // Searchbox container is visually at the top of the all apps UI but it's present in
            // end of the children list.
            // We need to move the searchbox to the top in a11y tree for a11y services to read the
            // all apps screen in same as visual order.
            arrayList.stream().filter(v -> v.getId() == R.id.search_container_all_apps)
                    .findFirst().ifPresent(v -> {
                        arrayList.remove(v);
                        arrayList.add(0, v);
                    });
        }
    }

    protected void updateHeaderScroll(int scrolledOffset) {
        float prog = Utilities.boundToRange((float) scrolledOffset / mHeaderThreshold, 0f, 1f);
        int headerColor = getHeaderColor(prog);
        int tabsAlpha = mHeader.getPeripheralProtectionHeight(/* expectedHeight */ false) == 0 ? 0
                : (int) (Utilities.boundToRange(
                        (scrolledOffset + mHeader.mSnappedScrolledY) / mHeaderThreshold, 0f, 1f)
                        * 255);
        if (headerColor != mHeaderColor || mTabsProtectionAlpha != tabsAlpha) {
            mHeaderColor = headerColor;
            mTabsProtectionAlpha = tabsAlpha;
            invalidateHeader();
        }
        if (mSearchUiManager.getEditText() == null) {
            return;
        }

        boolean bgVisible = mSearchUiManager.getBackgroundVisibility();
        if (scrolledOffset == 0 && !isSearching()) {
            bgVisible = true;
        } else if (scrolledOffset > mHeaderThreshold) {
            bgVisible = false;
        }
        mSearchUiManager.setBackgroundVisibility(bgVisible, 1 - prog);
    }

    protected int getHeaderColor(float blendRatio) {
        float alpha = blendRatio * LauncherPrefs.APP_DRAWER_OPACITY.get(getContext()) / 100;
        return isBackgroundBlurEnabled()
                ? ColorUtils.setAlphaComponent(mHeaderProtectionColor, (int) (alpha * 255))
                : ColorUtils.blendARGB(getBackgroundColor(), mHeaderProtectionColor, alpha);
    }

    int getBackgroundColor() {
        if (mUseCustomBackgroundColor) {
            return mCustomBackgroundColor;
        }
        return isBackgroundBlurEnabled()
                ? mBottomSheetBackgroundColorOverBlur
                : mBottomSheetBackgroundColorBlurFallback;
    }

    boolean isBackgroundBlurEnabled() {
        return mActivityContext.isAllAppsBackgroundBlurEnabled();
    }

    /**
     * @return true if the search bar is floating above this container (at the bottom of the screen)
     */
    protected boolean isSearchBarFloating() {
        return mSearchUiDelegate.isSearchBarFloating();
    }

    /**
     * Whether the <em>floating</em> search bar should appear as a small pill when not focused.
     * <p>
     * Note: This method mirrors one in LauncherState. For subclasses that use Launcher, it likely
     * makes sense to use that method to derive an appropriate value for the current/target state.
     */
    public boolean shouldFloatingSearchBarBePillWhenUnfocused() {
        return false;
    }

    /**
     * How far from the bottom of the screen the <em>floating</em> search bar should rest when the
     * IME is not present.
     * <p>
     * To hide offscreen, use a negative value.
     * <p>
     * Note: if the provided value is non-negative but less than the current bottom insets, the
     * insets will be applied. As such, you can use 0 to default to this.
     * <p>
     * Note: This method mirrors one in LauncherState. For subclasses that use Launcher, it likely
     * makes sense to use that method to derive an appropriate value for the current/target state.
     */
    public int getFloatingSearchBarRestingMarginBottom() {
        return 0;
    }

    /**
     * How far from the start of the screen the <em>floating</em> search bar should rest.
     * <p>
     * To use original margin, return a negative value.
     * <p>
     * Note: This method mirrors one in LauncherState. For subclasses that use Launcher, it likely
     * makes sense to use that method to derive an appropriate value for the current/target state.
     */
    public int getFloatingSearchBarRestingMarginStart() {
        DeviceProfile dp = mActivityContext.getDeviceProfile();
        return dp.getAllAppsProfile().getLeftRightMargin()
                + dp.getAllAppsIconStartMargin(mActivityContext);
    }

    /**
     * How far from the end of the screen the <em>floating</em> search bar should rest.
     * <p>
     * To use original margin, return a negative value.
     * <p>
     * Note: This method mirrors one in LauncherState. For subclasses that use Launcher, it likely
     * makes sense to use that method to derive an appropriate value for the current/target state.
     */
    public int getFloatingSearchBarRestingMarginEnd() {
        DeviceProfile dp = mActivityContext.getDeviceProfile();
        return dp.getAllAppsProfile().getLeftRightMargin()
                + dp.getAllAppsIconStartMargin(mActivityContext);
    }

    private String getSearchPlacement() {
        if (mSearchPlacement == null) {
            mSearchPlacement = LauncherPrefs.ALL_APPS_SEARCH_PLACEMENT.get(getContext());
        }
        return mSearchPlacement;
    }

    protected boolean isSearchBarHidden() {
        return SEARCH_PLACEMENT_HIDDEN.equals(getSearchPlacement());
    }

    protected boolean isSearchBarAtBottom() {
        return !isSearchBarFloating() && !isSearchBarHidden()
                && SEARCH_PLACEMENT_BOTTOM.equals(getSearchPlacement());
    }

    private int getSearchContainerBottomMargin() {
        int bottomArea = Math.max(Math.max(mInsets.bottom, mNavBarScrimHeight), mImeInsetBottom);
        return bottomArea + getResources().getDimensionPixelSize(
                R.dimen.all_apps_search_bar_bottom_padding_extra);
    }

    private void updateFastScrollerBottomMargin() {
        if (mSearchContainer == null) {
            return;
        }
        int bottomMargin = 0;
        if (isSearchBarFloating()) {
            bottomMargin = mSearchContainer.getHeight() + getResources().getDimensionPixelSize(
                    R.dimen.fastscroll_bottom_margin_floating_search);
        } else if (isSearchBarAtBottom()) {
            bottomMargin = mSearchContainer.getHeight() + getSearchContainerBottomMargin();
        }
        applyBottomMargin(mFastScroller, bottomMargin);
        applyBottomMargin(mFastScrollLetterLayout, bottomMargin);
    }

    private static void applyBottomMargin(@Nullable View v, int bottomMargin) {
        if (v == null || !(v.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
            return;
        }
        RelativeLayout.LayoutParams lp = (LayoutParams) v.getLayoutParams();
        if (lp.bottomMargin != bottomMargin) {
            lp.bottomMargin = bottomMargin;
            // Avoids a layout pass loop: only set when the value actually changed.
            v.setLayoutParams(lp);
        }
    }

    private void layoutSearchContainerBottom() {
        if (mSearchContainer == null
                || !(mSearchContainer.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
            return;
        }
        RelativeLayout.LayoutParams layoutParams = (LayoutParams) mSearchContainer.getLayoutParams();
        int bottomMargin = getSearchContainerBottomMargin();
        if (layoutParams.getRules()[RelativeLayout.ALIGN_PARENT_BOTTOM] == RelativeLayout.TRUE
                && layoutParams.bottomMargin == bottomMargin) {
            return;
        }
        layoutParams.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        layoutParams.bottomMargin = bottomMargin;
        mSearchContainer.setLayoutParams(layoutParams);
    }

    private void layoutSearchContainerTop() {
        if (mSearchContainer == null
                || !(mSearchContainer.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
            return;
        }
        RelativeLayout.LayoutParams layoutParams = (LayoutParams) mSearchContainer.getLayoutParams();
        layoutParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        layoutParams.bottomMargin = 0;
        mSearchContainer.setLayoutParams(layoutParams);
    }

    private void layoutAboveSearchContainer(View v, boolean includeTabsMargin) {
        if (!(v.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
            return;
        }

        RelativeLayout.LayoutParams layoutParams = (LayoutParams) v.getLayoutParams();
        // Stretch between the top of the container and the search bar.
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        layoutParams.addRule(RelativeLayout.ABOVE, R.id.search_container_all_apps);

        int topMargin = 0;
        if (includeTabsMargin) {
            topMargin += getContext().getResources().getDimensionPixelSize(
                    R.dimen.all_apps_header_pill_height)
                    + getContext().getResources().getDimensionPixelSize(
                            R.dimen.all_apps_tabs_margin_top);
        }
        layoutParams.topMargin = topMargin;
        layoutParams.bottomMargin = getContext().getResources().getDimensionPixelSize(
                R.dimen.all_apps_search_bar_bottom_adjustment);
    }

    private void layoutBelowSearchContainer(View v, boolean includeTabsMargin) {
        if (!(v.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
            return;
        }

        RelativeLayout.LayoutParams layoutParams = (LayoutParams) v.getLayoutParams();
        layoutParams.addRule(RelativeLayout.BELOW, R.id.search_container_all_apps);

        int topMargin = getContext().getResources().getDimensionPixelSize(
                R.dimen.all_apps_search_bar_bottom_adjustment);
        if (includeTabsMargin) {
            topMargin += getContext().getResources().getDimensionPixelSize(
                    R.dimen.all_apps_header_pill_height)
                    + getContext().getResources().getDimensionPixelSize(
                    R.dimen.all_apps_tabs_margin_top);
        }
        layoutParams.topMargin = topMargin;
    }

    private void alignParentTop(View v, boolean includeTabsMargin) {
        if (!(v.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
            return;
        }

        RelativeLayout.LayoutParams layoutParams = (LayoutParams) v.getLayoutParams();
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        layoutParams.topMargin =
                includeTabsMargin
                        ? getContext().getResources().getDimensionPixelSize(
                        R.dimen.all_apps_header_pill_height)
                        : 0;
    }

    private void removeCustomRules(View v) {
        if (!(v.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
            return;
        }

        RelativeLayout.LayoutParams layoutParams = (LayoutParams) v.getLayoutParams();
        layoutParams.removeRule(RelativeLayout.ABOVE);
        layoutParams.removeRule(RelativeLayout.ALIGN_TOP);
        layoutParams.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
        layoutParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        layoutParams.removeRule(RelativeLayout.BELOW);
    }

    protected BaseAllAppsAdapter createAdapter(AlphabeticalAppsList appsList) {
        return new AllAppsGridAdapter(mActivityContext, getLayoutInflater(), appsList,
                mMainAdapterProvider);
    }

    public boolean isInAllApps() {
        // TODO: Make this abstract
        return true;
    }

    /**
     * Inflates the search bar
     */
    protected View inflateSearchBar() {
        return mSearchUiDelegate.inflateSearchBar();
    }

    /** The adapter provider for the main section. */
    public final SearchAdapterProvider<?> getMainAdapterProvider() {
        return mMainAdapterProvider;
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> sparseArray) {
        try {
            // Many slice view id is not properly assigned, and hence throws null
            // pointer exception in the underneath method. Catching the exception
            // simply doesn't restore these slice views. This doesn't have any
            // user visible effect because because we query them again.
            super.dispatchRestoreInstanceState(sparseArray);
        } catch (Exception e) {
            Log.e("AllAppsContainerView", "restoreInstanceState viewId = 0", e);
        }

        Bundle state = (Bundle) sparseArray.get(R.id.work_tab_state_id, null);
        if (state != null) {
            int currentPage = state.getInt(BUNDLE_KEY_CURRENT_PAGE, 0);
            if (currentPage == AdapterHolder.WORK && mViewPager != null) {
                mViewPager.setCurrentPage(currentPage);
                rebindAdapters();
            } else {
                reset(true, false /* clearScrim */);
            }
        }
    }

    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        super.dispatchSaveInstanceState(container);
        Bundle state = new Bundle();
        state.putInt(BUNDLE_KEY_CURRENT_PAGE, getCurrentPage());
        container.put(R.id.work_tab_state_id, state);
    }

    /** @deprecated Get it from {@link ActivityContext#getActivityComponent()} */
    @Deprecated
    public AllAppsStore getAppsStore() {
        return mAllAppsStore;
    }

    public WorkProfileManager getWorkManager() {
        return mWorkManager;
    }

    /** Returns whether Private Profile has been setup. */
    public boolean hasPrivateProfile() {
        return mHasPrivateApps;
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        for (AdapterHolder holder : mAH) {
            holder.mAdapter.setAppsPerRow(dp.getAllAppsProfile().getNumShownAllAppsColumns());
            holder.mAppsList.setNumAppsPerRowAllApps(
                    getEffectiveAppsPerRow(holder, dp.getAllAppsProfile().getNumShownAllAppsColumns()));
            if (holder.mRecyclerView != null) {
                // Remove all views and clear the pool, while keeping the data same. After this
                // call, all the viewHolders will be recreated.
                holder.mRecyclerView.swapAdapter(holder.mRecyclerView.getAdapter(), true);
                holder.mRecyclerView.getRecycledViewPool().clear();
            }
        }
        if (mOneUiPagedView != null) {
            refreshOneUiApps();
            applyOneUiPagePadding();
        }

        int navBarScrimColor = Themes.getNavBarScrimColor(mActivityContext);
        if (mNavBarScrimPaint.getColor() != navBarScrimColor) {
            mNavBarScrimPaint.setColor(navBarScrimColor);
            invalidate();
        }
    }

    @VisibleForTesting
    public void onAppsUpdated() {
        Log.d(TAG, "onAppsUpdated; number of apps: " + mAllAppsStore.getApps().length);
        initProfileStatus();
        if (!isSearching()) {
            rebindAdapters();
        }
        // The paged style has no work tab / work utility views to reset.
        if (mHasWorkApps && !AppDrawerStyle.isVerticalPaged(mAppDrawerStyle)) {
            mWorkManager.reset();
        }
        if (mHasPrivateApps) {
            mPrivateProfileManager.reset();
        }

        mActivityContext.getStatsLogManager().logger()
                .withCardinality(mAllAppsStore.getApps().length)
                .log(LAUNCHER_ALLAPPS_COUNT);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // The AllAppsContainerView houses the QSB and is hence visible from the Workspace
        // Overview states. We shouldn't intercept for the scrubber in these cases.
        if (!isInAllApps()) {
            mTouchHandler = null;
            return false;
        }

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            AllAppsRecyclerView rv = getActiveRecyclerView();
            if (rv != null && rv.getScrollbar() != null
                    && rv.getScrollbar().isHitInParent(ev.getX(), ev.getY(), mFastScrollerOffset)) {
                mTouchHandler = rv.getScrollbar();
            } else {
                mTouchHandler = null;
            }
        }
        if (mTouchHandler != null) {
            return mTouchHandler.handleTouchEvent(ev, mFastScrollerOffset);
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isInAllApps()) {
            return false;
        }

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            AllAppsRecyclerView rv = getActiveRecyclerView();
            if (rv != null && rv.getScrollbar() != null
                    && rv.getScrollbar().isHitInParent(ev.getX(), ev.getY(), mFastScrollerOffset)) {
                mTouchHandler = rv.getScrollbar();
            } else {
                mTouchHandler = null;

            }
        }
        if (mTouchHandler != null) {
            mTouchHandler.handleTouchEvent(ev, mFastScrollerOffset);
            return true;
        }
        if (isSearching()
                && mActivityContext.getDragLayer().isEventOverView(getVisibleContainerView(), ev)) {
            // if in search state, consume touch event.
            return true;
        }
        return false;
    }

    /** The current active recycler view (A-Z list from one of the profiles, or search results). */
    public AllAppsRecyclerView getActiveRecyclerView() {
        if (isSearching()) {
            return getSearchRecyclerView();
        }
        return getActiveAppsRecyclerView();
    }

    /** Run some code on all the recycler views. */
    protected void forAllRecyclerViews(Consumer<AllAppsRecyclerView> consumer) {
        for (AdapterHolder holder : mAH) {
            if (holder.mRecyclerView == null) {
                continue;
            }
            consumer.accept(holder.mRecyclerView);
        }
    }

    /** The current focus change listener in the search container. */
    public OnFocusChangeListener getSearchFocusChangeListener() {
        return mAH.get(AdapterHolder.SEARCH).mOnFocusChangeListener;
    }

    /** The current apps recycler view in the container. */
    private AllAppsRecyclerView getActiveAppsRecyclerView() {
        if (!mUsingTabs || isPersonalTab()) {
            AllAppsRecyclerView rv = mAH.get(AdapterHolder.MAIN).mRecyclerView;
            if (rv == null && mOneUiPagedView != null) {
                rv = mOneUiPagedView.getCurrentRecyclerView();
            }
            return rv;
        } else {
            return mAH.get(AdapterHolder.WORK).mRecyclerView;
        }
    }

    private void initProfileStatus() {
        mHasWorkApps = Stream.of(mAllAppsStore.getApps())
                .anyMatch(mWorkManager.getItemInfoMatcher());
        mHasPrivateApps = Stream.of(mAllAppsStore.getApps())
                .anyMatch(mPrivateProfileManager.getItemInfoMatcher());
    }

    /**
     * The container for A-Z apps (the ViewPager for main+work tabs, or main RV). This is currently
     * hidden while searching.
     */
    public ViewGroup getAppsRecyclerViewContainer() {
        if (mViewPager != null) {
            return mViewPager;
        }
        if (mOneUiPagedView != null) {
            return mOneUiPagedView;
        }
        return findViewById(R.id.apps_list_view);
    }

    /** The RV for search results, which is hidden while A-Z apps are visible. */
    public SearchRecyclerView getSearchRecyclerView() {
        return mSearchRecyclerView;
    }

    protected boolean isPersonalTab() {
        return mViewPager == null || mViewPager.getNextPage() == 0;
    }

    /**
     * Switches the current page to the provided {@code tab} if tabs are supported, otherwise does
     * nothing.
     */
    public void switchToTab(int tab) {
        if (mUsingTabs) {
            mViewPager.setCurrentPage(tab);
        }
    }

    public LayoutInflater getLayoutInflater() {
        return mSearchUiDelegate.getLayoutInflater();
    }

    @Override
    public void onDropCompleted(View target, DragObject d, boolean success) {}

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        DeviceProfile grid = mActivityContext.getDeviceProfile();

        applyAdapterSideAndBottomPaddings(grid);

        MarginLayoutParams mlp = (MarginLayoutParams) getLayoutParams();
        // Ignore left/right insets on tablet because we are already centered in-screen.
        if (grid.getDeviceProperties().isLargeScreen()) {
            mlp.leftMargin = mlp.rightMargin = 0;
        } else {
            mlp.leftMargin = insets.left;
            mlp.rightMargin = insets.right;
        }
        setLayoutParams(mlp);

        if (!grid.isVerticalBarLayout() || FeatureFlags.enableResponsiveWorkspace()) {
            // Fullscreen styles start right below the status bar instead of the sheet offset.
            int topPadding = isFullscreenStyle()
                    ? insets.top
                    : grid.getAllAppsProfile().getPadding().top;
            setPadding(grid.getAllAppsProfile().getLeftRightMargin(), topPadding,
                    grid.getAllAppsProfile().getLeftRightMargin(), 0);
        }
        if (isSearchBarAtBottom()) {
            layoutSearchContainerBottom();
            updateFastScrollerBottomMargin();
        }
        updateOneUiPageIndicatorLayout();
        InsettableFrameLayout.dispatchInsets(this, insets);
    }

    /**
     * Returns a padding in case a scrim is shown on the bottom of the view and a padding is needed.
     */
    protected int computeNavBarScrimHeight(WindowInsets insets) {
        return 0;
    }

    /**
     * Returns the current height of nav bar scrim
     */
    public int getNavBarScrimHeight() {
        return mNavBarScrimHeight;
    }

    @Override
    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        mNavBarScrimHeight = computeNavBarScrimHeight(insets);
        mImeInsetBottom = insets.getInsets(WindowInsets.Type.ime()).bottom;
        applyAdapterSideAndBottomPaddings(mActivityContext.getDeviceProfile());
        if (isSearchBarAtBottom()) {
            layoutSearchContainerBottom();
            updateFastScrollerBottomMargin();
        }
        return super.dispatchApplyWindowInsets(insets);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (mNavBarScrimHeight > 0) {
            float left = (getWidth() - getWidth() / getScaleX()) / 2;
            float top = getHeight() / 2f + (getHeight() / 2f - mNavBarScrimHeight) / getScaleY();
            canvas.drawRect(left, top, getWidth() / getScaleX(),
                    top + mNavBarScrimHeight / getScaleY(), mNavBarScrimPaint);
        }
    }

    protected void setScrollbarVisibility(boolean visible) {
        AllAppsRecyclerView rv = getActiveRecyclerView();
        if (rv != null && rv.getScrollbar() != null) {
            rv.getScrollbar().setVisibility(visible ? VISIBLE : GONE);
        }
    }

    protected void updateSearchResultsVisibility() {
        if (isSearching()) {
            getSearchRecyclerView().setVisibility(VISIBLE);
            getAppsRecyclerViewContainer().setVisibility(GONE);
            mHeader.setVisibility(GONE);
        } else {
            getSearchRecyclerView().setVisibility(GONE);
            getAppsRecyclerViewContainer().setVisibility(VISIBLE);
            mHeader.setVisibility(VISIBLE);
        }
        if (mHeader.isSetUp()) {
            mHeader.setActiveRV(getCurrentPage());
        }
        updateOneUiPageIndicatorState();
    }

    private void applyAdapterSideAndBottomPaddings(DeviceProfile grid) {
        // In the paged style the page indicator (laid out above the nav bar) takes the insets.
        int bottomPadding = isSearchBarAtBottom() || mOneUiPagedView != null
                ? 0 : Math.max(mInsets.bottom, mNavBarScrimHeight);
        mAH.forEach(adapterHolder -> {
            adapterHolder.mPadding.bottom = bottomPadding;
            adapterHolder.mPadding.left = grid.getAllAppsProfile().getPadding().left;
            adapterHolder.mPadding.right = grid.getAllAppsProfile().getPadding().right;
            adapterHolder.applyPadding();
        });
        applyOneUiPagePadding();
    }

    private void drawFullscreenBackgroundOnScrim(Canvas canvas, float scale,
            @Px int bottomOffsetPx) {
        final float translationY = getTranslationY();
        final float width = canvas.getWidth();
        final float height = getHeight();
        final float horizontalScaleOffset = (1 - scale) * width / 2;
        final float verticalScaleOffset = (1 - scale) * height / 2;
        final boolean scaled = scale < 1f;

        final float top = getTop() + translationY + verticalScaleOffset;
        final float bottom = (scaled
                ? getTop() + translationY + height - verticalScaleOffset
                : Math.max(canvas.getHeight(), getBottom() + translationY)) + bottomOffsetPx;

        int backgroundColor = getBackgroundColor();
        mHeaderPaint.setColor(backgroundColor);
        mHeaderPaint.setAlpha(Color.alpha(backgroundColor));
        mTmpRectF.set(horizontalScaleOffset, top, width - horizontalScaleOffset, bottom);
        if (scaled) {
            // Show a rounded card while the predictive back gesture shrinks the drawer.
            if (mFullCornerRadii == null) {
                float r = Themes.getDialogCornerRadius(getContext());
                mFullCornerRadii = new float[]{r, r, r, r, r, r, r, r};
            }
            mTmpPath.reset();
            mTmpPath.addRoundRect(mTmpRectF, mFullCornerRadii, Direction.CW);
            canvas.drawPath(mTmpPath, mHeaderPaint);
        } else {
            canvas.drawRect(mTmpRectF, mHeaderPaint);
        }
    }

    private void updateAppDrawerStyle() {
        // Styles only apply to the home screen drawer; taskbar All Apps keeps the stock sheet.
        mAppDrawerStyle = mActivityContext instanceof Launcher
                ? AppDrawerStyle.get(getContext())
                : AppDrawerStyle.NORMAL;
    }

    /** Returns the {@link AppDrawerStyle} in use by this container. */
    public String getAppDrawerStyle() {
        return mAppDrawerStyle;
    }

    protected boolean isFullscreenStyle() {
        return AppDrawerStyle.isFullscreen(mAppDrawerStyle);
    }

    private boolean shouldShowFastScroller() {
        return LauncherPrefs.DRAWER_SCROLLBAR.get(getContext())
                && !AppDrawerStyle.isVerticalPaged(mAppDrawerStyle);
    }

    private int getEffectiveAppsPerRow(AdapterHolder holder, int appsPerRow) {
        return holder.mType != SEARCH && AppDrawerStyle.isHorizontalList(mAppDrawerStyle)
                ? 1 : appsPerRow;
    }

    private void bindOneUiPagedView() {
        if (mOneUiPagedView == null) {
            return;
        }
        mOneUiPagedView.setPageIndicator(mOneUiPageIndicator);
        mOneUiPagedView.setOnRecyclerViewsChangedListener((removed, added) -> {
            for (AllAppsRecyclerView rv : removed) {
                mAllAppsStore.unregisterIconContainer(rv);
            }
            for (AllAppsRecyclerView rv : added) {
                mAllAppsStore.registerIconContainer(rv);
            }
        });
        mOneUiPagedView.setOnActivePageChangedListener((recyclerView, page) -> {
            mAH.get(AdapterHolder.MAIN).mRecyclerView = recyclerView;
            if (mHeader != null && mHeader.isSetUp()) {
                mHeader.updateMainRV(recyclerView);
            }
            updateOneUiPageIndicatorState();
        });
        applyOneUiPagePadding();
        mOneUiPagedView.setApps(getOneUiApps());
        mAH.get(AdapterHolder.MAIN).mRecyclerView = mOneUiPagedView.getCurrentRecyclerView();
    }

    private void refreshOneUiApps() {
        if (mOneUiPagedView != null) {
            mOneUiPagedView.setApps(getOneUiApps());
        }
    }

    private void applyOneUiPagePadding() {
        if (mOneUiPagedView != null) {
            mOneUiPagedView.setPagePadding(mAH.get(AdapterHolder.MAIN).mPadding);
        }
    }

    /**
     * Regular (personal + work) apps in A-Z order. Private space apps are left out: they stay
     * reachable through search ("Private space"), which keeps the locked/unlocked flow intact.
     */
    private List<AppInfo> getOneUiApps() {
        List<AppInfo> apps = new ArrayList<>();
        for (AdapterItem item : mAH.get(AdapterHolder.MAIN).mAppsList.getAdapterItems()) {
            if (item.viewType == BaseAllAppsAdapter.VIEW_TYPE_ICON && item.itemInfo != null) {
                apps.add(item.itemInfo);
            }
        }
        return apps;
    }

    private void unregisterMainIconContainers() {
        if (mOneUiPagedView != null) {
            for (AllAppsRecyclerView rv : mOneUiPagedView.getRecyclerViews()) {
                mAllAppsStore.unregisterIconContainer(rv);
            }
        }
        mAllAppsStore.unregisterIconContainer(mAH.get(AdapterHolder.MAIN).mRecyclerView);
    }

    private void updateOneUiPageIndicatorLayout() {
        if (mOneUiPageIndicator == null
                || !(mOneUiPageIndicator.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
            return;
        }
        RelativeLayout.LayoutParams lp =
                (RelativeLayout.LayoutParams) mOneUiPageIndicator.getLayoutParams();
        lp.removeRule(RelativeLayout.ABOVE);
        lp.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        if (isSearchBarAtBottom()) {
            lp.addRule(RelativeLayout.ABOVE, R.id.search_container_all_apps);
            lp.bottomMargin = 0;
        } else {
            lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            int bottomMargin = Math.max(mInsets.bottom, mNavBarScrimHeight)
                    + getResources().getDimensionPixelSize(
                            R.dimen.all_apps_page_indicator_bottom_margin);
            if (isSearchBarFloating() && mSearchContainer != null) {
                bottomMargin += mSearchContainer.getHeight();
            }
            lp.bottomMargin = bottomMargin;
        }
        mOneUiPageIndicator.setLayoutParams(lp);
        updateOneUiPageIndicatorState();
    }

    private void updateOneUiPageIndicatorState() {
        if (mOneUiPageIndicator == null) {
            return;
        }
        if (mOneUiPagedView == null) {
            mOneUiPageIndicator.setVisibility(GONE);
            return;
        }
        // Stay INVISIBLE (not GONE) so the pages keep a stable height with one page or in search.
        boolean visible = !isSearching() && mOneUiPagedView.getPageCount() > 1;
        mOneUiPageIndicator.setVisibility(visible ? VISIBLE : INVISIBLE);
        if (visible) {
            mOneUiPageIndicator.setMarkersCount(mOneUiPagedView.getPageCount());
            mOneUiPageIndicator.setActiveMarker(mOneUiPagedView.getNextPage());
        }
    }

    private void setDeviceManagementResources() {
        StringCache cache = mActivityContext.getStringCache();
        if (cache != null) {
            Button personalTab = findViewById(R.id.tab_personal);
            personalTab.setText(cache.allAppsPersonalTab);

            Button workTab = findViewById(R.id.tab_work);
            workTab.setText(cache.allAppsWorkTab);
        }
    }

    /**
     * Returns true if the container has work apps.
     */
    public boolean shouldShowTabs() {
        // The paged style shows personal and work apps on the same pages.
        return mHasWorkApps && !AppDrawerStyle.isVerticalPaged(mAppDrawerStyle);
    }

    // Used by tests only
    private boolean isDescendantViewVisible(int viewId) {
        final View view = findViewById(viewId);
        if (view == null) return false;

        if (!view.isShown()) return false;

        return view.getGlobalVisibleRect(new Rect());
    }

    /** Called in Launcher#bindStringCache() to update the UI when cache is updated. */
    public Unit updateWorkUI() {
        setDeviceManagementResources();
        if (mWorkManager.getWorkUtilityView() != null) {
            mWorkManager.getWorkUtilityView().updateStringFromCache();
        }
        inflateWorkCardsIfNeeded();
        return Unit.INSTANCE;
    }

    private void inflateWorkCardsIfNeeded() {
        AllAppsRecyclerView workRV = mAH.get(AdapterHolder.WORK).mRecyclerView;
        if (workRV != null) {
            for (int i = 0; i < workRV.getChildCount(); i++) {
                View currentView  = workRV.getChildAt(i);
                int currentItemViewType = workRV.getChildViewHolder(currentView).getItemViewType();
                if (currentItemViewType == VIEW_TYPE_WORK_EDU_CARD) {
                    ((WorkEduCard) currentView).updateStringFromCache();
                } else if (currentItemViewType == VIEW_TYPE_WORK_DISABLED_CARD) {
                    ((WorkPausedCard) currentView).updateStringFromCache();
                }
            }
        }
    }

    @VisibleForTesting
    public void setWorkManager(WorkProfileManager workManager) {
        mWorkManager = workManager;
    }

    @VisibleForTesting
    public boolean isPersonalTabVisible() {
        return isDescendantViewVisible(R.id.tab_personal);
    }

    @VisibleForTesting
    public boolean isWorkTabVisible() {
        return isDescendantViewVisible(R.id.tab_work);
    }

    public AlphabeticalAppsList getSearchResultList() {
        return mAH.get(SEARCH).mAppsList;
    }

    public AlphabeticalAppsList getPersonalAppList() {
        return mAH.get(MAIN).mAppsList;
    }

    public AlphabeticalAppsList getWorkAppList() {
        return mAH.get(WORK).mAppsList;
    }

    public FloatingHeaderView getFloatingHeaderView() {
        return mHeader;
    }

    @VisibleForTesting
    public View getContentView() {
        return isSearching() ? getSearchRecyclerView() : getAppsRecyclerViewContainer();
    }

    /** The current page visible in all apps. */
    public int getCurrentPage() {
        return isSearching()
                ? SEARCH
                : mViewPager == null ? AdapterHolder.MAIN : mViewPager.getNextPage();
    }

    public PrivateProfileManager getPrivateProfileManager() {
        return mPrivateProfileManager;
    }

    /**
     * Adds an update listener to animator that adds springs to the animation.
     */
    public void addSpringFromFlingUpdateListener(ValueAnimator animator,
            float velocity /* release velocity */,
            float progress /* portion of the distance to travel*/) {
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                float distance = (1 - progress) * getHeight(); // px
                float settleVelocity = Math.min(0, distance
                        / (AllAppsTransitionController.INTERP_COEFF * animator.getDuration())
                        + velocity);
                absorbSwipeUpVelocity(Math.max(1000, Math.abs(
                        Math.round(settleVelocity * FLING_VELOCITY_MULTIPLIER))));
            }
        });
    }

    /** Invoked when the container is pulled. */
    public void onPull(float deltaDistance, float displacement) {
        absorbPullDeltaDistance(PULL_MULTIPLIER * deltaDistance, PULL_MULTIPLIER * displacement);
        // Current motion spec is to actually push and not pull
        // on this surface. However, until EdgeEffect.onPush (b/190612804) is
        // implemented at view level, we will simply pull
    }

    @Override
    public void getDrawingRect(Rect outRect) {
        super.getDrawingRect(outRect);
        outRect.offset(0, (int) getTranslationY());
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        invalidateHeader();
    }

    @Override
    public void setScaleY(float scaleY) {
        super.setScaleY(scaleY);
        if (mNavBarScrimHeight > 0) {
            // Call invalidate to prevent navbar scrim from scaling. The navbar scrim is drawn
            // directly onto the canvas. To prevent it from being scaled with the canvas, there's a
            // counter scale applied in dispatchDraw.
            invalidate(20, getHeight() - mNavBarScrimHeight, getWidth(), getHeight());
        }
    }

    /**
     * Set {@link Animator.AnimatorListener} on {@link #mAllAppsTransitionController} to observe
     * animation of backing out of all apps search view to all apps view.
     */
    public void setAllAppsSearchBackAnimatorListener(Animator.AnimatorListener listener) {
        Preconditions.assertNotNull(mAllAppsTransitionController);
        if (mAllAppsTransitionController == null) {
            return;
        }
        mAllAppsTransitionController.setAllAppsSearchBackAnimationListener(listener);
    }

    public void setScrimView(ScrimView scrimView) {
        mScrimView = scrimView;
    }

    @Override
    public void drawOnScrimWithScaleAndBottomOffset(
            Canvas canvas, float scale, @Px int bottomOffsetPx) {
        if (isFullscreenStyle()) {
            drawFullscreenBackgroundOnScrim(canvas, scale, bottomOffsetPx);
            return;
        }
        final View panel = mBottomSheetBackground;
        final float translationY = ((View) panel.getParent()).getTranslationY();

        final float horizontalScaleOffset = (1 - scale) * panel.getWidth() / 2;
        final float verticalScaleOffset = (1 - scale) * (panel.getHeight() - getHeight() / 2);
        // Left and right insets can be applied to this container, as well as the panel.
        float left = getLeft() + panel.getLeft();
        float right = left + panel.getWidth();

        final float topNoScale = panel.getTop() + translationY;
        final float topWithScale = topNoScale + verticalScaleOffset;
        final float leftWithScale = left + horizontalScaleOffset;
        final float rightWithScale = right - horizontalScaleOffset;
        final float bottomWithOffset = panel.getBottom() + bottomOffsetPx;
        // Draw full background panel if presenting on a sheet.
        int backgroundColor = getBackgroundColor();
        float backgroundAlpha = Color.alpha(backgroundColor) / 255.0f;
        mHeaderPaint.setColor(backgroundColor);
        mHeaderPaint.setAlpha((int) (backgroundAlpha * 255));

        mTmpRectF.set(
                leftWithScale,
                topWithScale,
                rightWithScale,
                bottomWithOffset);
        mTmpPath.reset();
        mTmpPath.addRoundRect(mTmpRectF, mBottomSheetCornerRadii, Direction.CW);
        canvas.drawPath(mTmpPath, mHeaderPaint);

        // TODO (b/414671116): Apply header protection whenever search bar is focused.
        // applyHeaderProtection(canvas, scale, backgroundAlpha, topNoScale, topWithScale,
        //         leftWithScale, rightWithScale);
    }

    private void applyHeaderProtection(Canvas canvas, float scale, float backgroundAlpha,
            float topNoScale, float topWithScale, float leftWithScale, float rightWithScale) {
        if (DEBUG_HEADER_PROTECTION) {
            mHeaderPaint.setColor(Color.MAGENTA);
            mHeaderPaint.setAlpha(255);
        } else {
            mHeaderPaint.setColor(mHeaderColor);
            mHeaderPaint.setAlpha((int) (getAlpha() * Color.alpha(mHeaderColor)));
        }

        // If header is not visible or only differs from the background with alpha, don't draw it.
        int headerWithoutAlpha = ColorUtils.setAlphaComponent(mHeaderPaint.getColor(), 0);
        int backgroundWithoutAlpha = ColorUtils.setAlphaComponent(getBackgroundColor(), 0);
        if (headerWithoutAlpha == backgroundWithoutAlpha || mHeaderPaint.getColor() == 0) {
            return;
        }

        mHeaderPaint.setAlpha((int) (mHeaderPaint.getAlpha() * backgroundAlpha));

        // Draw header on background panel
        final float headerBottomNoScale =
                getHeaderBottom() + getVisibleContainerView().getPaddingTop();
        final float headerHeightNoScale = headerBottomNoScale - topNoScale;
        final float headerBottomWithScaleOnTablet = topWithScale + headerHeightNoScale * scale;
        final FloatingHeaderView headerView = getFloatingHeaderView();
        // Start adding header protection if search bar or tabs will attach to the top.
        if (!isSearchBarFloating() || mUsingTabs) {
            mTmpRectF.set(
                    leftWithScale,
                    topWithScale,
                    rightWithScale,
                    headerBottomWithScaleOnTablet);
            mTmpPath.reset();
            mTmpPath.addRoundRect(mTmpRectF, mBottomSheetCornerRadii, Direction.CW);
            canvas.drawPath(mTmpPath, mHeaderPaint);
        }

        // If tab exist (such as work profile), extend header with tab height
        final int tabsHeight = headerView.getPeripheralProtectionHeight(/* expectedHeight */ false);
        if (mTabsProtectionAlpha > 0 && tabsHeight != 0) {
            if (DEBUG_HEADER_PROTECTION) {
                mHeaderPaint.setColor(Color.BLUE);
                mHeaderPaint.setAlpha(255);
            } else {
                mHeaderPaint.setAlpha((int) (getAlpha() * mTabsProtectionAlpha * backgroundAlpha));
            }

            final float tabTopWithScale = headerBottomWithScaleOnTablet;
            final float tabBottomWithScale = tabTopWithScale + tabsHeight * scale;

            canvas.drawRect(
                    leftWithScale,
                    tabTopWithScale,
                    rightWithScale,
                    tabBottomWithScale,
                    mHeaderPaint);
        }
    }

    /**
     * The height of the header protection as if the user scrolled down the app list.
     */
    float getHeaderProtectionHeight() {
        float headerBottom = getHeaderBottom() - getTranslationY();
        if (mUsingTabs) {
            return headerBottom + mHeader.getPeripheralProtectionHeight(/* expectedHeight */ true);
        } else {
            return headerBottom;
        }
    }

    ConstraintLayout getFastScrollerLetterList() {
        return mFastScrollLetterLayout;
    }

    /**
     * redraws header protection
     */
    public void invalidateHeader() {
        if (mScrimView != null) {
            mScrimView.invalidate();
        }
    }

    /** Returns the position of the bottom edge of the header */
    public int getHeaderBottom() {
        int bottom = (int) getTranslationY() + mHeader.getClipTop();
        if (isSearchBarFloating()) {
            return bottom + mBottomSheetBackground.getTop();
        }
        return bottom + mHeader.getTop();
    }

    boolean isUsingTabs() {
        return mUsingTabs;
    }

    /**
     * Returns a view that denotes the visible part of all apps container view.
     */
    public View getVisibleContainerView() {
        return mBottomSheetBackground;
    }

    protected void onInitializeRecyclerView(RecyclerView rv) {
        rv.addOnScrollListener(mScrollListener);
        mSearchUiDelegate.onInitializeRecyclerView(rv);
    }

    /** Returns the instance of @{code SearchTransitionController}. */
    public SearchTransitionController getSearchTransitionController() {
        return mSearchTransitionController;
    }

    /** Holds a {@link BaseAllAppsAdapter} and related fields. */
    public class AdapterHolder {
        public static final int MAIN = 0;
        public static final int WORK = 1;
        public static final int SEARCH = 2;

        private final int mType;
        public final BaseAllAppsAdapter mAdapter;
        final RecyclerView.LayoutManager mLayoutManager;
        final AlphabeticalAppsList mAppsList;
        final Rect mPadding = new Rect();
        AllAppsRecyclerView mRecyclerView;
        private OnFocusChangeListener mOnFocusChangeListener;

        AdapterHolder(int type, AlphabeticalAppsList appsList) {
            mType = type;
            mAppsList = appsList;
            mAdapter = createAdapter(mAppsList);
            if (type != SEARCH) {
                if (mAdapter instanceof AllAppsGridAdapter gridAdapter) {
                    gridAdapter.setDrawerStyle(mAppDrawerStyle);
                }
                if (AppDrawerStyle.isHorizontalList(mAppDrawerStyle)) {
                    mAppsList.setNumAppsPerRowAllApps(1);
                }
            }
            mAppsList.setAdapter(mAdapter);
            mLayoutManager = mAdapter.getLayoutManager();
        }

        void setup(@NonNull View rv, @Nullable Predicate<ItemInfo> matcher) {
            mAppsList.updateItemFilter(matcher);
            mRecyclerView = (AllAppsRecyclerView) rv;
            mRecyclerView.bindFastScrollbar(mFastScroller, ALL_APPS_SCROLLER);
            mRecyclerView.setEdgeEffectFactory(createEdgeEffectFactory());
            mRecyclerView.setApps(mAppsList);
            mRecyclerView.setLayoutManager(mLayoutManager);
            mRecyclerView.setAdapter(mAdapter);
            mRecyclerView.setHasFixedSize(true);
            // No animations will occur when changes occur to the items in this RecyclerView.
            mRecyclerView.setItemAnimator(null);
            onInitializeRecyclerView(mRecyclerView);
            // Use ViewGroupFocusHelper for SearchRecyclerView to draw focus outline for the
            // buttons in the view (e.g. query builder button and setting button)
            FocusedItemDecorator focusedItemDecorator = isSearch() ? new FocusedItemDecorator(
                    new ViewGroupFocusHelper(mRecyclerView)) : new FocusedItemDecorator(
                    mRecyclerView);
            mRecyclerView.addItemDecoration(focusedItemDecorator);
            mOnFocusChangeListener = focusedItemDecorator.getFocusListener();
            mAdapter.setIconFocusListener(mOnFocusChangeListener);
            applyPadding();
        }

        void applyPadding() {
            if (mRecyclerView != null) {
                int bottomOffset = 0;
                if (isWork() && mWorkManager.getWorkUtilityView() != null) {
                    bottomOffset =
                            mInsets.bottom + mWorkManager.getWorkUtilityView().getTotalHeight();
                } else if (isMain() && mPrivateProfileManager != null) {
                    Optional<AdapterItem> privateSpaceHeaderItem = mAppsList.getAdapterItems()
                            .stream()
                            .filter(item -> item.viewType == VIEW_TYPE_PRIVATE_SPACE_HEADER)
                            .findFirst();
                    if (privateSpaceHeaderItem.isPresent()) {
                        bottomOffset = mPrivateSpaceBottomExtraSpace;
                    }
                }
                if (isSearchBarFloating()) {
                    bottomOffset += mSearchContainer.getHeight();
                }
                mRecyclerView.setPadding(mPadding.left, mPadding.top, mPadding.right,
                        mPadding.bottom + bottomOffset);
            }
        }

        private boolean isWork() {
            return mType == WORK;
        }

        private boolean isSearch() {
            return mType == SEARCH;
        }

        private boolean isMain() {
            return mType == MAIN;
        }
    }
}
