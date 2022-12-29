/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;

import static com.android.launcher3.BubbleTextView.DISPLAY_TASKBAR;
import static com.android.launcher3.Flags.enableCursorHoverStates;
import static com.android.launcher3.Flags.enableRecentsInTaskbar;
import static com.android.launcher3.Flags.taskbarRecentsLayoutTransition;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER;
import static com.android.launcher3.config.FeatureFlags.enableTaskbarPinning;
import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;

import static java.util.function.Predicate.not;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.DisplayCutout;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.PreviewBackground;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.CollectionInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.taskbar.customization.TaskbarAllAppsButtonContainer;
import com.android.launcher3.taskbar.customization.TaskbarDividerContainer;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.LauncherBindableItemsContainer;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.views.TaskViewType;
import com.android.systemui.shared.recents.model.Task;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Hosts the Taskbar content such as Hotseat and Recent Apps. Drawn on top of other apps.
 */
public class TaskbarView extends FrameLayout implements FolderIcon.FolderIconParent, Insettable,
        DeviceProfile.OnDeviceProfileChangeListener {
    private static final Rect sTmpRect = new Rect();

    private final int[] mTempOutLocation = new int[2];
    private final Rect mIconLayoutBounds;
    private final int mIconTouchSize;
    private final int mItemMarginLeftRight;
    private final int mItemPadding;
    private final int mFolderLeaveBehindColor;
    private final boolean mIsRtl;

    private final TaskbarActivityContext mActivityContext;
    @Nullable private BubbleBarLocation mBubbleBarLocation = null;

    // Initialized in init.
    private TaskbarViewCallbacks mControllerCallbacks;
    private View.OnClickListener mIconClickListener;
    private View.OnLongClickListener mIconLongClickListener;

    // Only non-null when the corresponding Folder is open.
    @Nullable private FolderIcon mLeaveBehindFolderIcon;

    // Only non-null when device supports having an All Apps button.
    private final TaskbarAllAppsButtonContainer mAllAppsButtonContainer;

    // Only non-null when device supports having a Divider button.
    @Nullable private TaskbarDividerContainer mTaskbarDividerContainer;

    // Only non-null when device supports having a Taskbar Overflow button.
    @Nullable private TaskbarOverflowView mTaskbarOverflowView;

    private int mNextViewIndex;

    /**
     * Whether the divider is between Hotseat icons and Recents,
     * instead of between All Apps button and Hotseat.
     */
    private boolean mAddedDividerForRecents;

    private final View mQsb;

    private final float mTransientTaskbarMinWidth;

    private boolean mShouldTryStartAlign;

    private int mMaxNumIcons = 0;
    private int mIdealNumIcons = 0;

    private final int mAllAppsButtonTranslationOffset;

    private final int mNumStaticViews;

    public TaskbarView(@NonNull Context context) {
        this(context, null);
    }

    public TaskbarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskbarView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TaskbarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mActivityContext = ActivityContext.lookupContext(context);
        mIconLayoutBounds = mActivityContext.getTransientTaskbarBounds();
        Resources resources = getResources();
        mIsRtl = Utilities.isRtl(resources);
        mTransientTaskbarMinWidth = resources.getDimension(R.dimen.transient_taskbar_min_width);

        onDeviceProfileChanged(mActivityContext.getDeviceProfile());

        int actualMargin = resources.getDimensionPixelSize(R.dimen.taskbar_icon_spacing);
        int actualIconSize = mActivityContext.getDeviceProfile().taskbarIconSize;
        if (enableTaskbarPinning() && !mActivityContext.isThreeButtonNav()) {
            DeviceProfile deviceProfile = mActivityContext.getTransientTaskbarDeviceProfile();
            actualIconSize = deviceProfile.taskbarIconSize;
        }
        int visualIconSize = (int) (actualIconSize * ICON_VISIBLE_AREA_FACTOR);

        mIconTouchSize = Math.max(actualIconSize,
                resources.getDimensionPixelSize(R.dimen.taskbar_icon_min_touch_size));

        // We layout the icons to be of mIconTouchSize in width and height
        mItemMarginLeftRight = actualMargin - (mIconTouchSize - visualIconSize) / 2;

        // We always layout taskbar as a transient taskbar when we have taskbar pinning feature on,
        // then we scale and translate the icons to match persistent taskbar designs, so we use
        // taskbar icon size from current device profile to calculate correct item padding.
        mItemPadding = (mIconTouchSize - mActivityContext.getDeviceProfile().taskbarIconSize) / 2;
        mFolderLeaveBehindColor = Themes.getAttrColor(mActivityContext,
                android.R.attr.textColorTertiary);

        // Needed to draw folder leave-behind when opening one.
        setWillNotDraw(false);

        mAllAppsButtonContainer = new TaskbarAllAppsButtonContainer(context);
        mAllAppsButtonTranslationOffset =  (int) getResources().getDimension(
                mAllAppsButtonContainer.getAllAppsButtonTranslationXOffset(isTransientTaskbar()));

        if (enableTaskbarPinning() || enableRecentsInTaskbar()) {
            mTaskbarDividerContainer = new TaskbarDividerContainer(context);
        }

        if (Flags.taskbarOverflow()) {
            mTaskbarOverflowView = TaskbarOverflowView.inflateIcon(
                    R.layout.taskbar_overflow_view, this,
                    mIconTouchSize, mItemPadding);
        }

        // TODO: Disable touch events on QSB otherwise it can crash.
        if (Utilities.showQSB(context)) {
            mQsb = LayoutInflater.from(context).inflate(R.layout.search_container_hotseat, this, false);
        } else {
            mQsb = LayoutInflater.from(context).inflate(R.layout.empty_view, this, false);
        }

        mNumStaticViews = taskbarRecentsLayoutTransition() && !mActivityContext.isPhoneMode()
                ? addStaticViews() : 0;
    }

    /**
     * @return the maximum number of 'icons' that can fit in the taskbar.
     */
    private int calculateMaxNumIcons() {
        DeviceProfile deviceProfile = mActivityContext.getDeviceProfile();
        int availableWidth = deviceProfile.widthPx;
        int defaultEdgeMargin =
                (int) getResources().getDimension(deviceProfile.inv.inlineNavButtonsEndSpacing);
        int spaceForBubbleBar =
                Math.round(mControllerCallbacks.getBubbleBarMaxCollapsedWidthIfVisible());

        // Reserve space required for edge margins, or for navbar if shown. If task bar needs to be
        // center aligned with nav bar shown, reserve space on both sides.
        availableWidth -=
                Math.max(defaultEdgeMargin + spaceForBubbleBar, deviceProfile.hotseatBarEndOffset);
        availableWidth -= Math.max(
                defaultEdgeMargin + (mShouldTryStartAlign ? 0 : spaceForBubbleBar),
                mShouldTryStartAlign ? 0 : deviceProfile.hotseatBarEndOffset);

        // The space taken by an item icon used during layout.
        int iconSize = 2 * mItemMarginLeftRight + mIconTouchSize;

        int additionalIcons = 0;

        if (mTaskbarDividerContainer != null) {
            // Space for divider icon is reduced during layout compared to normal icon size, reserve
            // space for the divider separately.
            availableWidth -= iconSize - 4 * mItemMarginLeftRight;
            ++additionalIcons;
        }

        // All apps icon takes less space compared to normal icon size, reserve space for the icon
        // separately.
        boolean forceTransientTaskbarSize =
                enableTaskbarPinning() && !mActivityContext.isThreeButtonNav();
        availableWidth -= iconSize - (int) getResources().getDimension(
                mAllAppsButtonContainer.getAllAppsButtonTranslationXOffset(
                        forceTransientTaskbarSize || isTransientTaskbar()));
        ++additionalIcons;

        return Math.floorDiv(availableWidth, iconSize) + additionalIcons;
    }

    /**
     * Recalculates the max number of icons the taskbar view can show without entering overflow.
     * Returns whether the max number of icons changed and the change affects the number of icons
     * that should be shown in the taskbar.
     */
    boolean updateMaxNumIcons() {
        if (!Flags.taskbarOverflow()) {
            return false;
        }
        int oldMaxNumIcons = mMaxNumIcons;
        mMaxNumIcons = calculateMaxNumIcons();
        return oldMaxNumIcons != mMaxNumIcons
                && (mIdealNumIcons > oldMaxNumIcons || mIdealNumIcons > mMaxNumIcons);
    }

    /**
     * Pre-adds views that are always children of this view for LayoutTransition support.
     * <p>
     * Normally these views are removed and re-added when updating hotseat and recents. This
     * approach does not behave well with LayoutTransition, so we instead need to add them
     * initially and avoid removing them during updates.
     */
    private int addStaticViews() {
        int numStaticViews = 1;
        addView(mAllAppsButtonContainer);
        if (mActivityContext.getDeviceProfile().isQsbInline) {
            addView(mQsb, mIsRtl ? 1 : 0);
            mQsb.setVisibility(View.INVISIBLE);
            numStaticViews++;
        }
        return numStaticViews;
    }

    @Override
    public void setVisibility(int visibility) {
        boolean changed = getVisibility() != visibility;
        super.setVisibility(visibility);
        if (changed && mControllerCallbacks != null) {
            mControllerCallbacks.notifyVisibilityChanged();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mActivityContext.addOnDeviceProfileChangeListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mActivityContext.removeOnDeviceProfileChangeListener(this);
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        mShouldTryStartAlign = mActivityContext.shouldStartAlignTaskbar();
    }

    @Override
    public boolean performAccessibilityActionInternal(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
            announceTaskbarShown();
        } else if (action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
            announceTaskbarHidden();
        }
        return super.performAccessibilityActionInternal(action, arguments);
    }

    private void announceTaskbarShown() {
        BubbleBarLocation bubbleBarLocation = mControllerCallbacks.getBubbleBarLocationIfVisible();
        if (bubbleBarLocation == null) {
            announceForAccessibility(mContext.getString(R.string.taskbar_a11y_shown_title));
        } else if (bubbleBarLocation.isOnLeft(isLayoutRtl())) {
            announceForAccessibility(
                    mContext.getString(R.string.taskbar_a11y_shown_with_bubbles_left_title));
        } else {
            announceForAccessibility(
                    mContext.getString(R.string.taskbar_a11y_shown_with_bubbles_right_title));
        }
    }

    private void announceTaskbarHidden() {
        BubbleBarLocation bubbleBarLocation = mControllerCallbacks.getBubbleBarLocationIfVisible();
        if (bubbleBarLocation == null) {
            announceForAccessibility(mContext.getString(R.string.taskbar_a11y_hidden_title));
        } else {
            announceForAccessibility(
                    mContext.getString(R.string.taskbar_a11y_hidden_with_bubbles_title));
        }
    }

    protected void announceAccessibilityChanges() {
        this.performAccessibilityAction(
                isVisibleToUser() ? AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
                        : AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null);

        ActivityContext.lookupContext(getContext()).getDragLayer()
                .sendAccessibilityEvent(TYPE_WINDOW_CONTENT_CHANGED);
    }

    /**
     * Returns the icon touch size.
     */
    public int getIconTouchSize() {
        return mIconTouchSize;
    }

    protected void init(TaskbarViewCallbacks callbacks) {
        // set taskbar pane title so that accessibility service know it window and focuses.
        setAccessibilityPaneTitle(getContext().getString(R.string.taskbar_a11y_title));
        mControllerCallbacks = callbacks;
        mIconClickListener = mControllerCallbacks.getIconOnClickListener();
        mIconLongClickListener = mControllerCallbacks.getIconOnLongClickListener();

        mAllAppsButtonContainer.setUpCallbacks(callbacks);
        if (mTaskbarDividerContainer != null
                && mActivityContext.getTaskbarFeatureEvaluator().getSupportsPinningPopup()) {
            mTaskbarDividerContainer.setUpCallbacks(callbacks);
        }
        if (mTaskbarOverflowView != null) {
            mTaskbarOverflowView.setOnClickListener(
                    mControllerCallbacks.getOverflowOnClickListener());
            mTaskbarOverflowView.setOnLongClickListener(
                    mControllerCallbacks.getOverflowOnLongClickListener());
        }
        if (Flags.showTaskbarPinningPopupFromAnywhere()
                && mActivityContext.getTaskbarFeatureEvaluator().getSupportsPinningPopup()) {
            setOnTouchListener(mControllerCallbacks.getTaskbarTouchListener());
        }

        if (Flags.taskbarOverflow()) {
            mMaxNumIcons = calculateMaxNumIcons();
        }
    }

    private void removeAndRecycle(View view) {
        removeView(view);
        view.setOnClickListener(null);
        view.setOnLongClickListener(null);
        if (!(view.getTag() instanceof CollectionInfo)) {
            mActivityContext.getViewCache().recycleView(view.getSourceLayoutResId(), view);
        }
        if (view instanceof FolderIcon fi) {
            // We should clear FolderInfo's Folder and FolderIcon to avoid memory leak.
            fi.removeListeners();
        }
        view.setTag(null);
    }

    /** Loop through all {@link FolderIcon} as child views and clear listeners to avoid leak. */
    public void removeFolderIconListeners() {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (getChildAt(i) instanceof FolderIcon fi) {
                fi.removeListeners();
            }
        }
    }

    /** Inflates/binds the hotseat items and recent tasks to the view. */
    protected void updateItems(ItemInfo[] hotseatItemInfos, List<GroupTask> recentTasks) {
        // Filter out unsupported items.
        hotseatItemInfos = Arrays.stream(hotseatItemInfos)
                .filter(Objects::nonNull)
                .toArray(ItemInfo[]::new);
        // TODO(b/343289567 and b/316004172): support app pairs and desktop mode.
        recentTasks = recentTasks.stream().filter(not(GroupTask::supportsMultipleTasks)).toList();

        if (taskbarRecentsLayoutTransition()) {
            updateItemsWithLayoutTransition(hotseatItemInfos, recentTasks);
        } else {
            updateItemsWithoutLayoutTransition(hotseatItemInfos, recentTasks);
        }
    }

    private void updateItemsWithoutLayoutTransition(
            ItemInfo[] hotseatItemInfos, List<GroupTask> recentTasks) {

        mNextViewIndex = 0;
        mAddedDividerForRecents = false;

        removeView(mAllAppsButtonContainer);

        if (mTaskbarDividerContainer != null) {
            removeView(mTaskbarDividerContainer);
        }
        if (mTaskbarOverflowView != null) {
            removeView(mTaskbarOverflowView);
        }
        removeView(mQsb);

        updateHotseatItems(hotseatItemInfos);

        if (mTaskbarDividerContainer != null && !recentTasks.isEmpty()) {
            addView(mTaskbarDividerContainer, mNextViewIndex++);
            mAddedDividerForRecents = true;
        }

        updateRecents(recentTasks);

        addView(mAllAppsButtonContainer, mIsRtl ? hotseatItemInfos.length : 0);

        // If there are no recent tasks, add divider after All Apps (unless it's the only view).
        if (!mAddedDividerForRecents
                && mTaskbarDividerContainer != null
                && getChildCount() > 1) {
            addView(mTaskbarDividerContainer, mIsRtl ? (getChildCount() - 1) : 1);
        }

        if (mActivityContext.getDeviceProfile().isQsbInline) {
            addView(mQsb, mIsRtl ? getChildCount() : 0);
            // Always set QSB to invisible after re-adding.
            mQsb.setVisibility(View.INVISIBLE);
        }
    }

    private void updateItemsWithLayoutTransition(
            ItemInfo[] hotseatItemInfos, List<GroupTask> recentTasks) {

        // Skip static views and potential All Apps divider, if they are on the left.
        mNextViewIndex = mIsRtl ? 0 : mNumStaticViews;
        if (getChildAt(mNextViewIndex) == mTaskbarDividerContainer) {
            mNextViewIndex++;
        }

        // Update left section.
        if (mIsRtl) {
            updateRecents(recentTasks.reversed());
        } else {
            updateHotseatItems(hotseatItemInfos);
        }

        // Now at theoretical position for recent apps divider.
        updateRecentsDivider(!recentTasks.isEmpty());
        if (getChildAt(mNextViewIndex) == mTaskbarDividerContainer) {
            mNextViewIndex++;
        }

        // Update right section.
        if (mIsRtl) {
            updateHotseatItems(hotseatItemInfos);
        } else {
            updateRecents(recentTasks);
        }

        // Recents divider takes priority.
        if (!mAddedDividerForRecents) {
            updateAllAppsDivider();
        }
    }

    private void updateRecentsDivider(boolean hasRecents) {
        if (hasRecents && !mAddedDividerForRecents) {
            mAddedDividerForRecents = true;

            // Remove possible All Apps divider.
            if (getChildAt(mNumStaticViews) == mTaskbarDividerContainer) {
                mNextViewIndex--; // All Apps divider on the left. Need to account for removing it.
            }
            removeView(mTaskbarDividerContainer);

            addView(mTaskbarDividerContainer, mNextViewIndex);
        } else if (!hasRecents && mAddedDividerForRecents) {
            mAddedDividerForRecents = false;
            removeViewAt(mNextViewIndex);
        }
    }

    private void updateAllAppsDivider() {
        // Index where All Apps divider would be if it is already in Taskbar.
        final int expectedAllAppsDividerIndex =
                mIsRtl ? getChildCount() - mNumStaticViews - 1 : mNumStaticViews;
        if (getChildAt(expectedAllAppsDividerIndex) == mTaskbarDividerContainer
                && getChildCount() == mNumStaticViews + 1) {
            // Only static views with divider so remove divider.
            removeView(mTaskbarDividerContainer);
        } else if (getChildAt(expectedAllAppsDividerIndex) != mTaskbarDividerContainer
                && getChildCount() >= mNumStaticViews + 1) {
            // Static views with at least one app icon so add divider. For RTL, add it after the
            // icon that is at the expected index.
            addView(
                    mTaskbarDividerContainer,
                    mIsRtl ? expectedAllAppsDividerIndex + 1 : expectedAllAppsDividerIndex);
        }
    }

    private void updateHotseatItems(ItemInfo[] hotseatItemInfos) {
        int numViewsAnimated = 0;

        for (ItemInfo hotseatItemInfo : hotseatItemInfos) {
            // Replace any Hotseat views with the appropriate type if it's not already that type.
            final int expectedLayoutResId;
            boolean isCollection = false;
            if (hotseatItemInfo.isPredictedItem()) {
                expectedLayoutResId = R.layout.taskbar_predicted_app_icon;
            } else if (hotseatItemInfo instanceof CollectionInfo ci) {
                expectedLayoutResId = ci.itemType == ITEM_TYPE_APP_PAIR
                        ? R.layout.app_pair_icon
                        : R.layout.folder_icon;
                isCollection = true;
            } else {
                expectedLayoutResId = R.layout.taskbar_app_icon;
            }

            View hotseatView = null;
            while (isNextViewInSection(ItemInfo.class)) {
                hotseatView = getChildAt(mNextViewIndex);

                // see if the view can be reused
                if ((hotseatView.getSourceLayoutResId() != expectedLayoutResId)
                        || (isCollection && (hotseatView.getTag() != hotseatItemInfo))) {
                    // Unlike for BubbleTextView, we can't reapply a new FolderInfo after inflation,
                    // so if the info changes we need to reinflate. This should only happen if a new
                    // folder is dragged to the position that another folder previously existed.
                    removeAndRecycle(hotseatView);
                    hotseatView = null;
                } else {
                    // View found
                    break;
                }
            }

            if (hotseatView == null) {
                if (isCollection) {
                    CollectionInfo collectionInfo = (CollectionInfo) hotseatItemInfo;
                    switch (hotseatItemInfo.itemType) {
                        case ITEM_TYPE_FOLDER:
                            hotseatView = FolderIcon.inflateFolderAndIcon(
                                    expectedLayoutResId, mActivityContext, this,
                                    (FolderInfo) collectionInfo);
                            ((FolderIcon) hotseatView).setTextVisible(false);
                            break;
                        case ITEM_TYPE_APP_PAIR:
                            hotseatView = AppPairIcon.inflateIcon(
                                    expectedLayoutResId, mActivityContext, this,
                                    (AppPairInfo) collectionInfo, DISPLAY_TASKBAR);
                            ((AppPairIcon) hotseatView).setTextVisible(false);
                            break;
                        default:
                            throw new IllegalStateException(
                                    "Unexpected item type: " + hotseatItemInfo.itemType);
                    }
                } else {
                    hotseatView = inflate(expectedLayoutResId);
                }
                LayoutParams lp = new LayoutParams(mIconTouchSize, mIconTouchSize);
                hotseatView.setPadding(mItemPadding, mItemPadding, mItemPadding, mItemPadding);
                addView(hotseatView, mNextViewIndex, lp);
            }

            // Apply the Hotseat ItemInfos, or hide the view if there is none for a given index.
            if (hotseatView instanceof BubbleTextView btv
                    && hotseatItemInfo instanceof WorkspaceItemInfo workspaceInfo) {
                boolean animate = btv.shouldAnimateIconChange((WorkspaceItemInfo) hotseatItemInfo);
                btv.applyFromWorkspaceItem(workspaceInfo, animate, numViewsAnimated);
                if (animate) {
                    numViewsAnimated++;
                }
            }
            setClickAndLongClickListenersForIcon(hotseatView);
            if (enableCursorHoverStates()) {
                setHoverListenerForIcon(hotseatView);
            }
            mNextViewIndex++;
        }

        while (isNextViewInSection(ItemInfo.class)) {
            removeAndRecycle(getChildAt(mNextViewIndex));
        }
    }

    private void updateRecents(List<GroupTask> recentTasks) {
        // At this point, the all apps button has not been added as a child view, but needs to be
        // accounted for when comparing current icon count to max number of icons.
        int nonTaskIconsToBeAdded = 1;

        boolean supportsOverflow = Flags.taskbarOverflow();
        int overflowSize = 0;
        if (supportsOverflow) {
            mIdealNumIcons = mNextViewIndex + recentTasks.size() + nonTaskIconsToBeAdded;
            overflowSize = mIdealNumIcons - mMaxNumIcons;

            if (overflowSize > 0 && mTaskbarOverflowView != null) {
                addView(mTaskbarOverflowView, mNextViewIndex++);
            } else if (mTaskbarOverflowView != null) {
                mTaskbarOverflowView.clearItems();
            }
        }

        List<Task> overflownTasks = null;
        // An extra item needs to be added to overflow button to account for the space taken up by
        // the overflow button.
        final int itemsToAddToOverflow =
                (overflowSize > 0) ? Math.min(overflowSize + 1, recentTasks.size()) : 0;
        if (overflowSize > 0) {
            overflownTasks = new ArrayList<>(itemsToAddToOverflow);
        }

        // Add Recent/Running icons.
        for (GroupTask task : recentTasks) {
            if (mTaskbarOverflowView != null && overflownTasks != null
                    && overflownTasks.size() < itemsToAddToOverflow) {
                // TODO(b/343289567 and b/316004172): support app pairs and desktop mode.
                overflownTasks.add(task.task1);
                if (overflownTasks.size() == itemsToAddToOverflow) {
                    mTaskbarOverflowView.setItems(overflownTasks);
                }
                continue;
            }

            // Replace any Recent views with the appropriate type if it's not already that type.
            final int expectedLayoutResId;
            boolean isCollection = false;
            if (task.supportsMultipleTasks()) {
                if (task.taskViewType == TaskViewType.DESKTOP) {
                    // TODO(b/316004172): use Desktop tile layout.
                    expectedLayoutResId = -1;
                } else {
                    // TODO(b/343289567): use R.layout.app_pair_icon
                    expectedLayoutResId = -1;
                }
                isCollection = true;
            } else {
                expectedLayoutResId = R.layout.taskbar_app_icon;
            }

            View recentIcon = null;
            while (isNextViewInSection(GroupTask.class)) {
                recentIcon = getChildAt(mNextViewIndex);

                // see if the view can be reused
                if ((recentIcon.getSourceLayoutResId() != expectedLayoutResId)
                        || (isCollection && (recentIcon.getTag() != task))) {
                    removeAndRecycle(recentIcon);
                    recentIcon = null;
                } else {
                    // View found
                    break;
                }
            }

            if (recentIcon == null) {
                // TODO(b/343289567 and b/316004172): support app pairs and desktop mode.
                recentIcon = inflate(expectedLayoutResId);
                LayoutParams lp = new LayoutParams(mIconTouchSize, mIconTouchSize);
                recentIcon.setPadding(mItemPadding, mItemPadding, mItemPadding, mItemPadding);
                addView(recentIcon, mNextViewIndex, lp);
            }

            if (recentIcon instanceof BubbleTextView btv) {
                applyGroupTaskToBubbleTextView(btv, task);
            }
            setClickAndLongClickListenersForIcon(recentIcon);
            if (enableCursorHoverStates()) {
                setHoverListenerForIcon(recentIcon);
            }
            mNextViewIndex++;
        }

        while (isNextViewInSection(GroupTask.class)) {
            removeAndRecycle(getChildAt(mNextViewIndex));
        }
    }

    private boolean isNextViewInSection(Class<?> tagClass) {
        return mNextViewIndex < getChildCount()
                && tagClass.isInstance(getChildAt(mNextViewIndex).getTag());
    }

    /** Binds the GroupTask to the BubbleTextView to be ready to present to the user. */
    public void applyGroupTaskToBubbleTextView(BubbleTextView btv, GroupTask groupTask) {
        // TODO(b/343289567): support app pairs.
        Task task1 = groupTask.task1;
        // TODO(b/344038728): use FastBitmapDrawable instead of Drawable, to get disabled state
        //  while dragging.
        Drawable taskIcon = groupTask.task1.icon;
        if (taskIcon != null) {
            taskIcon = taskIcon.getConstantState().newDrawable().mutate();
        }
        btv.applyIconAndLabel(taskIcon, task1.titleDescription);
        btv.setTag(groupTask);
    }

    /**
     * Sets OnClickListener and OnLongClickListener for the given view.
     */
    public void setClickAndLongClickListenersForIcon(View icon) {
        icon.setOnClickListener(mIconClickListener);
        icon.setOnLongClickListener(mIconLongClickListener);
        // Add right-click support to btv icons.
        icon.setOnTouchListener((v, event) -> {
            if (event.isFromSource(InputDevice.SOURCE_MOUSE)
                    && (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0
                    && v instanceof BubbleTextView) {
                mActivityContext.showPopupMenuForIcon((BubbleTextView) v);
                return true;
            }
            return false;
        });
    }

    /**
     * Sets OnHoverListener for the given view.
     */
    private void setHoverListenerForIcon(View icon) {
        icon.setOnHoverListener(mControllerCallbacks.getIconOnHoverListener(icon));
    }

    /** Updates taskbar icons accordingly to the new bubble bar location. */
    public void onBubbleBarLocationUpdated(BubbleBarLocation location) {
        if (mBubbleBarLocation == location) return;
        mBubbleBarLocation = location;
        requestLayout();
    }

    /**
     * Returns translation X for the taskbar icons for provided {@link BubbleBarLocation}. If the
     * bubble bar is not enabled, or location of the bubble bar is the same, or taskbar is not start
     * aligned - returns 0.
     */
    public float getTranslationXForBubbleBarPosition(BubbleBarLocation location) {
        if (!mControllerCallbacks.isBubbleBarEnabledInPersistentTaskbar()
                || location == mBubbleBarLocation
                || !mActivityContext.shouldStartAlignTaskbar()
        ) {
            return 0;
        }
        Rect iconsBounds = getTransientTaskbarIconLayoutBoundsInParent();
        return getTaskBarIconsEndForBubbleBarLocation(location) - iconsBounds.right;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int spaceNeeded = getIconLayoutWidth();
        boolean layoutRtl = isLayoutRtl();
        DeviceProfile deviceProfile = mActivityContext.getDeviceProfile();
        int navSpaceNeeded = deviceProfile.hotseatBarEndOffset;
        int centerAlignIconEnd = (right + left + spaceNeeded) / 2;
        int iconEnd = centerAlignIconEnd;
        if (mShouldTryStartAlign) {
            int startSpacingPx = deviceProfile.inlineNavButtonsEndSpacingPx;
            if (mControllerCallbacks.isBubbleBarEnabledInPersistentTaskbar()
                    && mBubbleBarLocation != null
                    && mActivityContext.shouldStartAlignTaskbar()) {
                iconEnd = (int) getTaskBarIconsEndForBubbleBarLocation(mBubbleBarLocation);
            } else {
                if (layoutRtl) {
                    iconEnd = right - startSpacingPx;
                } else {
                    iconEnd = startSpacingPx + spaceNeeded;
                }
                boolean needMoreSpaceForNav = layoutRtl
                        ? navSpaceNeeded > (iconEnd - spaceNeeded)
                        : iconEnd > (right - navSpaceNeeded);
                if (needMoreSpaceForNav) {
                    // Add offset to account for nav bar when taskbar is centered
                    int offset = layoutRtl
                            ? navSpaceNeeded - (centerAlignIconEnd - spaceNeeded)
                            : (right - navSpaceNeeded) - centerAlignIconEnd;
                    iconEnd = centerAlignIconEnd + offset;
                }
            }
        }

        // Currently, we support only one device with display cutout and we only are concern about
        // it when the bottom rect is present and non empty
        DisplayCutout displayCutout = getDisplay().getCutout();
        if (displayCutout != null && !displayCutout.getBoundingRectBottom().isEmpty()) {
            Rect cutoutBottomRect = displayCutout.getBoundingRectBottom();
            // when cutout present at the bottom of screen align taskbar icons to cutout offset
            // if taskbar icon overlaps with cutout
            int taskbarIconLeftBound = iconEnd - spaceNeeded;
            int taskbarIconRightBound = iconEnd;

            boolean doesTaskbarIconsOverlapWithCutout =
                    taskbarIconLeftBound <= cutoutBottomRect.centerX()
                            && cutoutBottomRect.centerX() <= taskbarIconRightBound;

            if (doesTaskbarIconsOverlapWithCutout) {
                if (!layoutRtl) {
                    iconEnd = spaceNeeded + cutoutBottomRect.width();
                } else {
                    iconEnd = right - cutoutBottomRect.width();
                }
            }
        }

        sTmpRect.set(mIconLayoutBounds);

        // Layout the children
        mIconLayoutBounds.right = iconEnd;
        mIconLayoutBounds.top = (bottom - top - mIconTouchSize) / 2;
        mIconLayoutBounds.bottom = mIconLayoutBounds.top + mIconTouchSize;

        // With rtl layout, the all apps button will be translated by `allAppsButtonOffset` after
        // layout completion (by `TaskbarViewController`). Offset the icon end by the same amount
        // when laying out icons, so the taskbar content remains centered after all apps button
        // translation.
        if (layoutRtl) {
            iconEnd += mAllAppsButtonTranslationOffset;
        }

        int count = getChildCount();
        for (int i = count; i > 0; i--) {
            View child = getChildAt(i - 1);
            if (child == mQsb) {
                int qsbStart;
                int qsbEnd;
                if (layoutRtl) {
                    qsbStart = iconEnd + mItemMarginLeftRight;
                    qsbEnd = qsbStart + deviceProfile.hotseatQsbWidth;
                } else {
                    qsbEnd = iconEnd - mItemMarginLeftRight;
                    qsbStart = qsbEnd - deviceProfile.hotseatQsbWidth;
                }
                int qsbTop = (bottom - top - deviceProfile.hotseatQsbHeight) / 2;
                int qsbBottom = qsbTop + deviceProfile.hotseatQsbHeight;
                child.layout(qsbStart, qsbTop, qsbEnd, qsbBottom);
            } else if (child == mTaskbarDividerContainer) {
                iconEnd += mItemMarginLeftRight;
                int iconStart = iconEnd - mIconTouchSize;
                child.layout(iconStart, mIconLayoutBounds.top, iconEnd, mIconLayoutBounds.bottom);
                iconEnd = iconStart + mItemMarginLeftRight;
            } else {
                iconEnd -= mItemMarginLeftRight;
                int iconStart = iconEnd - mIconTouchSize;
                child.layout(iconStart, mIconLayoutBounds.top, iconEnd, mIconLayoutBounds.bottom);
                iconEnd = iconStart - mItemMarginLeftRight;
            }
        }

        mIconLayoutBounds.left = iconEnd;

        // Adjust the icon layout bounds by the amount by which all apps button will be translated
        // post layout to maintain margin between all apps button and the edge of the transient
        // taskbar background. Done for ltr layout only - for rtl layout, the offset needs to be
        // adjusted on the right, which is done by offsetting `iconEnd` after setting
        // `mIconLayoutBounds.right`.
        if (!layoutRtl) {
            mIconLayoutBounds.left += mAllAppsButtonTranslationOffset;
        }

        if (mIconLayoutBounds.right - mIconLayoutBounds.left < mTransientTaskbarMinWidth) {
            int center = mIconLayoutBounds.centerX();
            int distanceFromCenter = (int) mTransientTaskbarMinWidth / 2;
            mIconLayoutBounds.right = center + distanceFromCenter;
            mIconLayoutBounds.left = center - distanceFromCenter;
        }

        if (!sTmpRect.equals(mIconLayoutBounds)) {
            mControllerCallbacks.notifyIconLayoutBoundsChanged();
        }
    }

    /**
     * Returns whether the given MotionEvent, *in screen coordinates*, is within any Taskbar item's
     * touch bounds.
     */
    public boolean isEventOverAnyItem(MotionEvent ev) {
        getLocationOnScreen(mTempOutLocation);
        int xInOurCoordinates = (int) ev.getRawX() - mTempOutLocation[0];
        int yInOurCoordinates = (int) ev.getRawY() - mTempOutLocation[1];
        return isShown() && getTaskbarIconsActualBounds().contains(xInOurCoordinates,
                yInOurCoordinates);
    }

    /**
     * Returns the current visual taskbar icons bounds (unlike `mIconLayoutBounds` which contains
     * bounds for transient mode only).
     */
    private Rect getTaskbarIconsActualBounds() {
        View[] iconViews = getIconViews();
        if (iconViews.length == 0) {
            return new Rect();
        }

        int[] firstIconViewLocation = new int[2];
        int[] lastIconViewLocation = new int[2];
        iconViews[0].getLocationOnScreen(firstIconViewLocation);
        iconViews[iconViews.length - 1].getLocationOnScreen(lastIconViewLocation);

        return new Rect(firstIconViewLocation[0], 0, lastIconViewLocation[0] + mIconTouchSize,
                getHeight());
    }

    /**
     * Gets visual bounds of the taskbar view. The visual bounds correspond to the taskbar touch
     * area, rather than layout placement in the parent view.
     */
    public Rect getTransientTaskbarIconLayoutBounds() {
        return new Rect(mIconLayoutBounds);
    }

    /** Gets taskbar layout bounds in parent view. */
    public Rect getTransientTaskbarIconLayoutBoundsInParent() {
        Rect actualBounds = new Rect(mIconLayoutBounds);
        actualBounds.top = getTop();
        actualBounds.bottom = getBottom();
        return actualBounds;
    }

    /**
     * Returns the space used by the icons
     */
    private int getIconLayoutWidth() {
        int countExcludingQsb = getChildCount();
        DeviceProfile deviceProfile = mActivityContext.getDeviceProfile();
        if (deviceProfile.isQsbInline) {
            countExcludingQsb--;
        }

        int iconLayoutBoundsWidth =
                countExcludingQsb * (mItemMarginLeftRight * 2 + mIconTouchSize);

        if (enableTaskbarPinning() && countExcludingQsb > 1) {
            // We are removing 4 * mItemMarginLeftRight as there should be no space between
            // All Apps icon, divider icon, and first app icon in taskbar
            iconLayoutBoundsWidth -= mItemMarginLeftRight * 4;
        }

        // The all apps button container gets offset horizontally, reducing the overall taskbar
        // view size.
        iconLayoutBoundsWidth -= mAllAppsButtonTranslationOffset;

        return iconLayoutBoundsWidth;
    }

    /**
     * Returns the app icons currently shown in the taskbar. The returned list does not include qsb,
     * but it includes all apps button and icon divider views.
     */
    public View[] getIconViews() {
        final int count = getChildCount();
        if (count == 0) {
            return new View[0];
        }
        View[] icons = new View[count - (mActivityContext.getDeviceProfile().isQsbInline ? 1 : 0)];
        int insertionPoint = 0;
        for (int i = 0; i < count; i++) {
            if (getChildAt(i)  == mQsb) continue;
            icons[insertionPoint++] = getChildAt(i);
        }
        return icons;
    }

    /**
     * The max number of icon views the taskbar can have when taskbar overflow is enabled.
     */
    int getMaxNumIconViews() {
        return mMaxNumIcons;
    }

    /**
     * Returns the all apps button in the taskbar.
     */
    public TaskbarAllAppsButtonContainer getAllAppsButtonContainer() {
        return mAllAppsButtonContainer;
    }

    /**
     * Returns the taskbar divider in the taskbar.
     */
    @Nullable
    public TaskbarDividerContainer getTaskbarDividerViewContainer() {
        return mTaskbarDividerContainer;
    }

    /**
     * Returns the taskbar overflow view in the taskbar.
     */
    @Nullable
    public TaskbarOverflowView getTaskbarOverflowView() {
        return mTaskbarOverflowView;
    }

    /**
     * Returns whether the divider is between Hotseat icons and Recents,
     * instead of between All Apps button and Hotseat.
     */
    public boolean isDividerForRecents() {
        return mAddedDividerForRecents;
    }

    /**
     * Returns the QSB in the taskbar.
     */
    public View getQsb() {
        return mQsb;
    }

    // FolderIconParent implemented methods.

    @Override
    public void drawFolderLeaveBehindForIcon(FolderIcon child) {
        mLeaveBehindFolderIcon = child;
        invalidate();
    }

    @Override
    public void clearFolderLeaveBehind(FolderIcon child) {
        mLeaveBehindFolderIcon = null;
        invalidate();
    }

    // End FolderIconParent implemented methods.

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mLeaveBehindFolderIcon != null) {
            canvas.save();
            canvas.translate(
                    mLeaveBehindFolderIcon.getLeft() + mLeaveBehindFolderIcon.getTranslationX(),
                    mLeaveBehindFolderIcon.getTop());
            PreviewBackground previewBackground = mLeaveBehindFolderIcon.getFolderBackground();
            previewBackground.drawLeaveBehind(canvas, mFolderLeaveBehindColor);
            canvas.restore();
        }
    }

    private View inflate(@LayoutRes int layoutResId) {
        return mActivityContext.getViewCache().getView(layoutResId, mActivityContext, this);
    }

    @Override
    public void setInsets(Rect insets) {
        // Ignore, we just implement Insettable to draw behind system insets.
    }

    private boolean isTransientTaskbar() {
        return DisplayController.isTransientTaskbar(mActivityContext)
                && !mActivityContext.isPhoneMode();
    }

    public boolean areIconsVisible() {
        // Consider the overall visibility
        return getVisibility() == VISIBLE;
    }

    /**
     * @return The all apps button horizontal offset used to calculate the taskbar contents width
     * during layout.
     */
    public int getAllAppsButtonTranslationXOffsetUsedForLayout() {
        return mAllAppsButtonTranslationOffset;
    }

    /**
     * Maps {@code op} over all the child views.
     */
    public void mapOverItems(LauncherBindableItemsContainer.ItemOperator op) {
        // map over all the shortcuts on the taskbar
        for (int i = 0; i < getChildCount(); i++) {
            View item = getChildAt(i);
            // TODO(b/344657629): Support GroupTask as well for notification dots/popup
            if (item.getTag() instanceof ItemInfo itemInfo && op.evaluate(itemInfo, item)) {
                return;
            }
        }
    }

    /**
     * Finds the first icon to match one of the given matchers, from highest to lowest priority.
     *
     * @return The first match, or All Apps button if no match was found.
     */
    public View getFirstMatch(Predicate<ItemInfo>... matchers) {
        for (Predicate<ItemInfo> matcher : matchers) {
            for (int i = 0; i < getChildCount(); i++) {
                View item = getChildAt(i);
                if (!(item.getTag() instanceof ItemInfo)) {
                    // Should only happen for All Apps button.
                    // Will also happen for Recent/Running app icons. (Which have GroupTask as tags)
                    continue;
                }
                ItemInfo info = (ItemInfo) item.getTag();
                if (matcher.test(info)) {
                    return item;
                }
            }
        }
        return mAllAppsButtonContainer;
    }

    /**
     * This method only works for bubble bar enabled in persistent task bar and the taskbar is start
     * aligned.
     */
    private float getTaskBarIconsEndForBubbleBarLocation(BubbleBarLocation location) {
        DeviceProfile deviceProfile = mActivityContext.getDeviceProfile();
        boolean navbarOnRight = location.isOnLeft(isLayoutRtl());
        int navSpaceNeeded = deviceProfile.hotseatBarEndOffset;
        if (navbarOnRight) {
            return getWidth() - navSpaceNeeded;
        } else {
            return navSpaceNeeded + getIconLayoutWidth();
        }
    }
}
