/*
 * Copyright (C) 2026 crDroid Android Project
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

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_PRIVATESPACE;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.app.animation.Interpolators;

import com.android.launcher3.Alarm;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.PreviewBackground;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.StatsLogManager.LauncherEvent;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemFactory;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.LabelComparator;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.views.OptionsPopupView;
import com.android.launcher3.views.OptionsPopupView.OptionItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Folders of the app drawer and drag and drop inside the drawer. */
public class DrawerFolderController implements DragController.DragListener,
        DrawerFoldersProvider {

    /** Time a new folder stays in place for its create animation. */
    private static final int CREATE_ANIMATION_DURATION = 500;
    private static final int LAND_ANIMATION_DURATION = 350;
    private static final int LAND_TIMEOUT = 300;
    private static final int HOME_TARGET_FADE_DURATION = 150;
    private static final int PAGE_FLIP_DELAY = 500;
    private static final float HOVER_SLOP_FRACTION = 0.25f;

    private final Launcher mLauncher;
    private final List<FolderInfo> mFolders = new ArrayList<>();
    /** Cached icons, so binding never re-inflates a folder. */
    private final SparseArray<FolderIcon> mIcons = new SparseArray<>();
    private int mIconGeneration = 0;

    private final Set<ComponentKey> mShownMemberKeys = new HashSet<>();
    private final List<FolderInfo> mShownFolders = new ArrayList<>();

    private final DrawerDropTarget mDrawerDropTarget = new DrawerDropTarget();
    private final HomeDropTarget mHomeDropTarget = new HomeDropTarget();
    private final TextView mHomeTargetView;

    private boolean mDragInDrawer = false;
    private boolean mDrawerSession = false;
    // Ignore the home target until the drag has left it once.
    private boolean mHomeTargetArmed = false;
    @Nullable private DropTarget.DragObject mDragObject;
    @Nullable private DragOptions mDragOptions;

    @Nullable private View mHoverView;
    @Nullable private FolderIcon mHoverFolderIcon;
    @Nullable private AppInfo mHoverApp;
    @Nullable private CreateFolderFeedback mCreateFeedback;

    // New folder playing its create animation in the drag layer, then moving into the list.
    @Nullable private FolderIcon mCreatingIcon;
    @Nullable private FolderIcon mLandingIcon;
    @Nullable private ViewGroup mLandingHost;
    @Nullable private ValueAnimator mLandingAnimator;
    private boolean mLandingKeepTop;
    private final Runnable mLandRunnable = this::landCreatedFolder;
    private final Runnable mLandTimeoutRunnable = this::onLandTimeout;

    private final Runnable mAutoScroller = this::autoScrollStep;
    private int mAutoScrollSpeed = 0;
    private final Alarm mPageFlipAlarm = new Alarm(Looper.getMainLooper());
    private int mPageFlipDirection = 0;

    private final float[] mTmpCenter = new float[2];
    private final int[] mTmpPoint = new int[2];
    private final Rect mTmpRect = new Rect();

    public DrawerFolderController(Launcher launcher) {
        mLauncher = launcher;
        mHomeTargetView = createHomeTargetView();
        mPageFlipAlarm.setOnAlarmListener(alarm -> flipPage());
    }

    public void addDropTargets(DragController dragController) {
        dragController.addDropTarget(mDrawerDropTarget);
        dragController.addDropTarget(mHomeDropTarget);
    }

    public void syncFromModel(List<FolderInfo> folders) {
        Set<FolderInfo> kept = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        kept.addAll(folders);
        for (int i = mIcons.size() - 1; i >= 0; i--) {
            FolderIcon icon = mIcons.valueAt(i);
            if (!kept.contains(icon.mInfo)) {
                discardIcon(icon);
                mIcons.removeAt(i);
            }
        }
        mFolders.clear();
        mFolders.addAll(folders);
        notifyChanged();
    }

    public void onItemsRemoved(Predicate<ItemInfo> matcher) {
        boolean changed = false;
        for (FolderInfo folder : new ArrayList<>(mFolders)) {
            List<ItemInfo> removed = new ArrayList<>();
            for (ItemInfo item : folder.getContents()) {
                if (matcher.test(item)) {
                    removed.add(item);
                }
            }
            if (removed.isEmpty()) {
                continue;
            }
            changed = true;
            FolderIcon icon = mIcons.get(folder.id);
            if (icon != null && icon.mInfo == folder) {
                Folder openFolder = icon.getFolder();
                openFolder.removeFolderContent(false, removed.toArray(new ItemInfo[0]));
                if (openFolder.isOpen()) {
                    openFolder.close(false /* animate */);
                }
            } else {
                folder.getContents().removeAll(removed);
            }
            if (mFolders.contains(folder) && folder.getContents().size() <= 1) {
                dissolveFolder(folder);
            }
        }
        if (changed) {
            notifyChanged();
        }
    }

    public void onItemsUpdated(Set<ItemInfo> updates) {
        for (int i = 0; i < mIcons.size(); i++) {
            FolderIcon icon = mIcons.valueAt(i);
            icon.updatePreviewItems(updates::contains);
            if (updates.contains(icon.mInfo)) {
                icon.onItemsChanged(false);
            }
        }
    }

    public void updateNotificationDots(Predicate<ItemInfo> matcher) {
        for (int i = 0; i < mIcons.size(); i++) {
            FolderIcon icon = mIcons.valueAt(i);
            if (icon.mInfo.anyMatch(matcher)) {
                icon.updateDotInfo();
            }
        }
    }

    /** Removes the folder; its apps show up in the drawer again. */
    public void dissolveFolder(FolderInfo info) {
        if (!mFolders.remove(info)) {
            return;
        }
        mLauncher.getModelWriter().deleteCollectionAndContentsFromDatabase(info);
        FolderIcon icon = mIcons.get(info.id);
        if (icon != null && icon.mInfo == info) {
            mIcons.remove(info.id);
            discardIcon(icon);
        }
        notifyChanged();
    }

    public void onFolderClosed(Folder folder) {
        if (mFolders.contains(folder.getInfo())) {
            refreshIfStale();
        }
    }

    private void notifyChanged() {
        ActivityAllAppsContainerView<?> appsView = mLauncher.getAppsView();
        if (appsView != null) {
            appsView.onDrawerFoldersChanged();
        }
    }

    private void refreshIfStale() {
        if (mCreatingIcon != null) {
            // Refreshed once the new folder lands.
            return;
        }
        if (!getFolderMemberKeysInternal().equals(mShownMemberKeys)
                || !getSortedFolders().equals(mShownFolders)) {
            notifyChanged();
        }
    }

    private void discardIcon(FolderIcon icon) {
        if (icon == mCreatingIcon) {
            mCreatingIcon = null;
            mLauncher.getDragLayer().removeCallbacks(mLandRunnable);
        }
        if (icon == mLandingIcon) {
            cancelLandingAnimation();
            mLandingIcon = null;
            mLandingHost = null;
            mLauncher.getDragLayer().removeCallbacks(mLandTimeoutRunnable);
        }
        icon.animate().cancel();
        icon.setTranslationX(0);
        icon.setTranslationY(0);
        icon.setAlpha(1f);
        Folder folder = icon.getFolder();
        if (folder.isOpen()) {
            folder.close(false);
        }
        ViewParent parent = icon.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(icon);
        }
        mIconGeneration++;
    }

    private void discardAllIcons() {
        for (int i = 0; i < mIcons.size(); i++) {
            discardIcon(mIcons.valueAt(i));
        }
        mIcons.clear();
    }

    @Override
    public List<FolderInfo> getDrawerFolders() {
        List<FolderInfo> folders = getSortedFolders();
        mShownFolders.clear();
        mShownFolders.addAll(folders);
        return folders;
    }

    private List<FolderInfo> getSortedFolders() {
        List<FolderInfo> result = new ArrayList<>(mFolders.size());
        for (FolderInfo folder : mFolders) {
            if (!folder.getContents().isEmpty()) {
                result.add(folder);
            }
        }
        LabelComparator labelComparator = new LabelComparator();
        result.sort(Comparator.<FolderInfo, String>comparing(
                f -> f.title == null ? "" : f.title.toString(), labelComparator)
                .thenComparingInt(f -> f.id));
        return result;
    }

    @Override
    public Set<ComponentKey> getFolderMemberKeys() {
        Set<ComponentKey> keys = getFolderMemberKeysInternal();
        mShownMemberKeys.clear();
        mShownMemberKeys.addAll(keys);
        return keys;
    }

    private Set<ComponentKey> getFolderMemberKeysInternal() {
        Set<ComponentKey> keys = new HashSet<>();
        for (FolderInfo folder : mFolders) {
            for (WorkspaceItemInfo item : folder.getAppContents()) {
                ComponentKey key = item.getComponentKey();
                if (key != null) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    @Override
    public int getIconGeneration() {
        return mIconGeneration;
    }

    @Override
    public void bindFolderIcon(FolderInfo info, ViewGroup host, boolean horizontal,
            int textColor) {
        FolderIcon icon = mIcons.get(info.id);
        if (icon != null && (icon.mInfo != info || icon.isDrawerHorizontal() != horizontal)) {
            mIcons.remove(info.id);
            discardIcon(icon);
            icon = null;
        }
        if (icon == null) {
            icon = inflateIcon(info, horizontal);
            mIcons.put(info.id, icon);
        }
        host.setTag(info);
        icon.getFolderName().setTextColor(textColor);
        if (icon == mCreatingIcon) {
            host.removeAllViews();
            return;
        }
        if (icon.getParent() == mLauncher.getDragLayer()) {
            host.removeAllViews();
            FolderIcon landingIcon = icon;
            host.post(() -> flyIntoHost(landingIcon, info, host));
            return;
        }
        attachToHost(icon, host);
    }

    private static void attachToHost(FolderIcon icon, ViewGroup host) {
        if (icon.getParent() == host) {
            return;
        }
        ViewParent parent = icon.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(icon);
        }
        icon.setTranslationX(0);
        icon.setTranslationY(0);
        icon.setAlpha(1f);
        host.removeAllViews();
        host.addView(icon, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
    }

    private FolderIcon inflateIcon(FolderInfo info, boolean horizontal) {
        FolderIcon icon = FolderIcon.inflateFolderAndIcon(R.layout.all_apps_folder_icon,
                mLauncher, mLauncher.getDragLayer(), info);
        icon.setDrawerHorizontal(horizontal);
        icon.setOnLongClickListener(this::onFolderLongClick);
        return icon;
    }

    private boolean onFolderLongClick(View v) {
        if (!(v instanceof FolderIcon icon) || !mFolders.contains(icon.mInfo)
                || icon == mCreatingIcon || icon.getFolder().isOpen()
                || mLauncher.getDragController().isDragging()
                || !mLauncher.isInState(ALL_APPS)) {
            return false;
        }
        FolderInfo info = icon.mInfo;
        icon.getPreviewBounds(mTmpRect);
        mTmpPoint[0] = 0;
        mTmpPoint[1] = 0;
        float scale = mLauncher.getDragLayer().getDescendantCoordRelativeToSelf(icon, mTmpPoint);
        RectF target = new RectF(
                mTmpPoint[0] + mTmpRect.left * scale,
                mTmpPoint[1] + mTmpRect.top * scale,
                mTmpPoint[0] + mTmpRect.right * scale,
                mTmpPoint[1] + mTmpRect.bottom * scale);

        List<OptionItem> options = new ArrayList<>();
        options.add(new OptionItem(mLauncher, R.string.all_apps_remove_folder,
                R.drawable.ic_remove_no_shadow, LauncherEvent.IGNORE, view -> {
                    removeFolder(info);
                    return true;
                }));
        OptionsPopupView.show(mLauncher, target, options, true /* shouldAddArrow */);
        return true;
    }

    public void removeFolder(FolderInfo info) {
        dissolveFolder(info);
    }

    @Override
    public void onDrawerDropCompleted() {
        refreshIfStale();
    }

    @Override
    public void onDeviceProfileChanged() {
        discardAllIcons();
    }

    public boolean isDragInDrawer() {
        return mDragInDrawer;
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        mDragInDrawer = isDrawerDrag(dragObject, options);
        mDrawerSession = mDragInDrawer;
        mHomeTargetArmed = false;
        if (!mDragInDrawer) {
            return;
        }
        mDragObject = dragObject;
        mDragOptions = options;
        showHomeTarget(true);
    }

    @Override
    public void onDragEnd() {
        clearHover();
        stopEdgeScroll();
        showHomeTarget(false);
        mDragInDrawer = false;
        mDragObject = null;
        mDragOptions = null;
        if (mDrawerSession) {
            mDrawerSession = false;
            refreshIfStale();
        }
    }

    private boolean isDrawerDrag(DropTarget.DragObject d, DragOptions options) {
        if (options.isAccessibleDrag || options.isKeyboardDrag
                || !mLauncher.isInState(ALL_APPS)) {
            return false;
        }
        ActivityAllAppsContainerView<?> apps = mLauncher.getAppsView();
        if (apps == null || !apps.supportsDrawerFolders()) {
            return false;
        }
        if (d.dragSource == apps) {
            return d.dragInfo instanceof AppInfo info && canBeInFolder(info);
        }
        return d.dragSource instanceof Folder folder && folder.isInAppDrawer();
    }

    private boolean canBeInFolder(AppInfo info) {
        if (info.container == CONTAINER_PRIVATESPACE) {
            return false;
        }
        PrivateProfileManager privateProfileManager =
                mLauncher.getAppsView().getPrivateProfileManager();
        return privateProfileManager == null
                || !privateProfileManager.getItemInfoMatcher().test(info);
    }

    private void handOverToHomeScreen(DropTarget.DragObject d) {
        if (!mDragInDrawer) {
            return;
        }
        mDragInDrawer = false;
        clearHover();
        stopEdgeScroll();
        showHomeTarget(false);

        if (d.dragSource instanceof Folder folder && d.dragInfo instanceof WorkspaceItemInfo) {
            // Copy it, the app stays in its folder.
            WorkspaceItemInfo original = (WorkspaceItemInfo) d.dragInfo;
            WorkspaceItemInfo copy = new WorkspaceItemInfo(original);
            copy.id = ItemInfo.NO_ID;
            copy.container = ItemInfo.NO_ID;
            copy.rank = 0;
            copy.cellX = -1;
            copy.cellY = -1;
            d.dragInfo = copy;
            folder.restoreDraggedItem(original);
        }
        DragOptions options = mDragOptions != null ? mDragOptions : new DragOptions();
        mLauncher.getWorkspace().onDrawerDragHandedOver(d, options);
        mLauncher.getDropTargetBar().onDrawerDragHandedOver(d, options);
    }

    private int getDrawerSheetTop() {
        ActivityAllAppsContainerView<?> apps = mLauncher.getAppsView();
        mLauncher.getDragLayer().getDescendantRectRelativeToSelf(apps, mTmpRect);
        return mTmpRect.top + apps.getPaddingTop();
    }

    private TextView createHomeTargetView() {
        TextView view = new TextView(mLauncher, null, 0, R.style.DropTargetButton);
        view.setText(R.string.add_to_home_screen);
        view.setContentDescription(view.getText());
        view.setGravity(Gravity.CENTER);
        view.setFocusable(false);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        view.setVisibility(View.GONE);

        BaseDragLayer.LayoutParams lp = new BaseDragLayer.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        lp.customPosition = true;
        lp.ignoreInsets = true;
        // Added before any drag view so drag views draw above it.
        mLauncher.getDragLayer().addView(view, lp);
        return view;
    }

    private void showHomeTarget(boolean show) {
        TextView view = mHomeTargetView;
        view.animate().cancel();
        if (show) {
            styleHomeTarget(view);
            positionHomeTarget(view);
            view.setAlpha(0f);
            view.setVisibility(View.VISIBLE);
            view.animate().alpha(1f).setDuration(HOME_TARGET_FADE_DURATION).start();
        } else if (view.getVisibility() == View.VISIBLE) {
            view.animate().alpha(0f).setDuration(HOME_TARGET_FADE_DURATION)
                    .withEndAction(() -> view.setVisibility(View.GONE)).start();
        }
    }

    private void styleHomeTarget(TextView view) {
        int background = AppDrawerStyle.getSearchBackgroundColor(mLauncher);
        int content = AppDrawerStyle.getSearchContentColor(mLauncher,
                mLauncher.getColor(R.color.materialColorOnSurface));

        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.RECTANGLE);
        pill.setCornerRadius(mLauncher.getResources().getDimension(
                R.dimen.drop_target_button_frame_radius));
        pill.setColor(background);
        view.setBackground(pill);
        view.setTextColor(content);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                mLauncher.getDeviceProfile().getDropTargetProfile().getTextSizePx());
        view.setTypeface(Typeface.create("variable-title-medium", Typeface.NORMAL));

        Drawable icon = mLauncher.getDrawable(R.drawable.ic_all_apps_drag_home);
        if (icon != null) {
            icon = icon.mutate();
            int size = mLauncher.getResources().getDimensionPixelSize(
                    R.dimen.drop_target_button_drawable_size);
            icon.setBounds(0, 0, size, size);
            icon.setTint(content);
        }
        view.setCompoundDrawablesRelative(icon, null, null, null);
    }

    /** Over the search bar if shown, so app rows stay reachable; else at the top. */
    private void positionHomeTarget(TextView view) {
        DragLayer dragLayer = mLauncher.getDragLayer();
        int maxWidth = Math.max(0, dragLayer.getWidth() - 2 * getEdgeSize());
        view.measure(View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int width = view.getMeasuredWidth();
        int height = Math.max(view.getMeasuredHeight(),
                mLauncher.getDeviceProfile().getDropTargetProfile().getBarSizePx());

        int centerY;
        View search = mLauncher.getAppsView().getVisibleSearchContainer();
        if (search != null) {
            dragLayer.getDescendantRectRelativeToSelf(search, mTmpRect);
            centerY = mTmpRect.centerY();
        } else {
            centerY = dragLayer.getInsets().top
                    + mLauncher.getDeviceProfile().getDropTargetProfile().getBarTopMarginPx()
                    + height / 2;
        }
        BaseDragLayer.LayoutParams lp = (BaseDragLayer.LayoutParams) view.getLayoutParams();
        lp.width = width;
        lp.height = height;
        lp.x = (dragLayer.getWidth() - width) / 2;
        lp.y = Math.max(dragLayer.getInsets().top, centerY - height / 2);
        view.setLayoutParams(lp);
    }

    private void getHomeTargetHitRect(Rect outRect) {
        BaseDragLayer.LayoutParams lp =
                (BaseDragLayer.LayoutParams) mHomeTargetView.getLayoutParams();
        int slop = mLauncher.getDeviceProfile().getDropTargetProfile().getDragPaddingPx();
        outRect.set(lp.x - slop, lp.y - slop, lp.x + lp.width + slop, lp.y + lp.height + slop);
    }

    private boolean isAboveDrawer(int y) {
        return y < mLauncher.getDragLayer().getInsets().top || y < getDrawerSheetTop();
    }

    private class HomeDropTarget implements DropTarget {

        @Override
        public boolean isDropEnabled() {
            return mDragInDrawer && mHomeTargetArmed;
        }

        @Override
        public void onDrop(DragObject dragObject, DragOptions options) { }

        @Override
        public void onDragEnter(DragObject dragObject) {
            handOverToHomeScreen(dragObject);
        }

        @Override
        public void onDragOver(DragObject dragObject) { }

        @Override
        public void onDragExit(DragObject dragObject) { }

        @Override
        public void prepareAccessibilityDrop() { }

        @Override
        public boolean acceptDrop(DragObject dragObject) {
            return false;
        }

        @Override
        public void getHitRectRelativeToDragLayer(Rect outRect) {
            getHomeTargetHitRect(outRect);
        }

        @Override
        public View getDropView() {
            return null;
        }
    }

    private class DrawerDropTarget implements DropTarget {

        @Override
        public boolean isDropEnabled() {
            return mDragInDrawer;
        }

        @Override
        public void getHitRectRelativeToDragLayer(Rect outRect) {
            DragLayer dragLayer = mLauncher.getDragLayer();
            outRect.set(0, 0, dragLayer.getWidth(), dragLayer.getHeight());
        }

        @Override
        public View getDropView() {
            return null;
        }

        @Override
        public void onDragEnter(DragObject d) {
            onDragOver(d);
        }

        @Override
        public void onDragOver(DragObject d) {
            if (!mDragInDrawer) {
                return;
            }
            if (!mHomeTargetArmed) {
                getHomeTargetHitRect(mTmpRect);
                mHomeTargetArmed = !mTmpRect.contains(d.x, d.y);
            }
            if (isAboveDrawer(d.y)) {
                handOverToHomeScreen(d);
                return;
            }
            updateHover(d);
            updateEdgeScroll(d);
        }

        @Override
        public void onDragExit(DragObject d) {
            if (d.dragComplete) {
                return;
            }
            clearHover();
            stopEdgeScroll();
        }

        @Override
        public void prepareAccessibilityDrop() { }

        @Override
        public boolean acceptDrop(DragObject d) {
            return mHoverView != null || d.dragSource instanceof Folder;
        }

        @Override
        public void onDrop(DragObject d, DragOptions options) {
            FolderIcon hoverFolder = mHoverFolderIcon;
            AppInfo hoverApp = mHoverApp;
            View hoverView = mHoverView;
            CreateFolderFeedback feedback = hoverApp != null ? mCreateFeedback : null;
            if (feedback != null) {
                mCreateFeedback = null;
            }
            clearHover();
            stopEdgeScroll();

            if (hoverFolder != null) {
                hoverFolder.onDrop(d, false);
            } else if (hoverApp != null) {
                createFolder(hoverApp, hoverView, feedback, d);
            } else if (d.dragSource instanceof Folder && d.dragInfo != null) {
                d.deferDragViewCleanupPostAnimation = false;
                // Moves the item out of its folder.
                mLauncher.getModelWriter().deleteItemFromDatabase(
                        d.dragInfo, "drawer folder item moved to the app drawer");
            }
            refreshIfStale();
        }
    }

    private void createFolder(AppInfo target, @Nullable View targetView,
            @Nullable CreateFolderFeedback feedback, DropTarget.DragObject d) {
        ItemInfo dragged = d.dragInfo instanceof WorkspaceItemFactory factory
                ? factory.makeWorkspaceItem(mLauncher)
                : d.dragInfo;
        if (dragged == null || !FolderInfo.willAcceptItemType(dragged.itemType)) {
            if (feedback != null) {
                feedback.release();
            }
            d.deferDragViewCleanupPostAnimation = false;
            return;
        }
        landCreatedFolder();

        FolderInfo info = new FolderInfo();
        mLauncher.getModelWriter().addItemToDatabase(info, CONTAINER_ALL_APPS, 0, 0, 0);
        mFolders.add(info);
        FolderIcon icon = inflateIcon(info,
                AppDrawerStyle.isHorizontalList(mLauncher.getAppsView().getAppDrawerStyle()));
        mIcons.put(info.id, icon);
        WorkspaceItemInfo targetItem = target.makeWorkspaceItem(mLauncher);

        DragLayer dragLayer = mLauncher.getDragLayer();
        Rect location = new Rect();
        float scale = 1f;
        boolean animate = d.dragView != null && targetView instanceof BubbleTextView
                && targetView.isAttachedToWindow() && targetView.getWidth() > 0
                && targetView.getHeight() > 0;
        if (animate) {
            scale = dragLayer.getDescendantRectRelativeToSelf(targetView, location);
        }
        if (!animate) {
            if (feedback != null) {
                feedback.release();
            }
            d.deferDragViewCleanupPostAnimation = false;
            Folder folder = icon.getFolder();
            folder.addFolderContent(targetItem);
            folder.addFolderContent(dragged);
            suggestFolderName(info, d);
            return;
        }

        // Where the app is, below the drag view.
        BaseDragLayer.LayoutParams lp =
                new BaseDragLayer.LayoutParams(location.width(), location.height());
        lp.customPosition = true;
        lp.ignoreInsets = true;
        lp.x = location.left;
        lp.y = location.top;
        int dragViewIndex = dragLayer.indexOfChild(d.dragView);
        dragLayer.addView(icon, dragViewIndex >= 0 ? dragViewIndex : -1, lp);
        icon.measure(View.MeasureSpec.makeMeasureSpec(lp.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(lp.height, View.MeasureSpec.EXACTLY));
        icon.layout(lp.x, lp.y, lp.x + lp.width, lp.y + lp.height);
        if (feedback != null) {
            feedback.remove();
            feedback.mBackground.animateToRest();
            icon.setFolderBackground(feedback.mBackground);
        }
        targetView.setVisibility(View.INVISIBLE);

        mCreatingIcon = icon;
        icon.performCreateAnimation(targetItem, targetView, dragged, d, location, scale);
        dragLayer.postDelayed(mLandRunnable, CREATE_ANIMATION_DURATION);
    }

    private void suggestFolderName(FolderInfo info, DropTarget.DragObject d) {
        if (d.folderNameSuggestionLoader == null) {
            return;
        }
        InstanceId instanceId = d.logInstanceId;
        d.folderNameSuggestionLoader.getSuggestedFolderName(
                new ArrayList<>(info.getAppContents()),
                nameInfos -> MAIN_EXECUTOR.execute(() -> {
                    FolderIcon current = mIcons.get(info.id);
                    if (mFolders.contains(info) && current != null && current.mInfo == info) {
                        current.setLabelSuggestion(nameInfos, instanceId);
                        refreshIfStale();
                    }
                }));
    }

    private void landCreatedFolder() {
        DragLayer dragLayer = mLauncher.getDragLayer();
        dragLayer.removeCallbacks(mLandRunnable);
        FolderIcon icon = mCreatingIcon;
        if (icon == null) {
            return;
        }
        mCreatingIcon = null;
        if (icon.getParent() != dragLayer) {
            return;
        }
        finishLanding();
        mLandingIcon = icon;
        // The new folder is inserted above the first visible row; keep the list at the top.
        AllAppsRecyclerView rv = mLauncher.getAppsView().getActiveRecyclerView();
        mLandingKeepTop = rv != null && !rv.canScrollVertically(-1);
        mIconGeneration++;
        notifyChanged();
        dragLayer.postDelayed(mLandTimeoutRunnable, LAND_TIMEOUT);
    }

    private void flyIntoHost(FolderIcon icon, FolderInfo info, ViewGroup host) {
        DragLayer dragLayer = mLauncher.getDragLayer();
        if (mIcons.get(info.id) != icon || host.getTag() != info) {
            return;
        }
        if (icon.getParent() == null) {
            attachToHost(icon, host);
            return;
        }
        if (icon.getParent() != dragLayer) {
            return;
        }
        if (!host.isAttachedToWindow()) {
            if (mLandingIcon == icon) {
                finishLanding();
            } else {
                removeFromDragLayer(icon);
            }
            return;
        }
        dragLayer.removeCallbacks(mLandTimeoutRunnable);
        mLandingIcon = icon;
        mLandingHost = host;
        icon.animate().cancel();
        icon.setAlpha(1f);
        if (mLandingKeepTop && host.getParent() instanceof RecyclerView rv
                && rv.canScrollVertically(-1)) {
            rv.scrollToPosition(0);
        }
        mLandingKeepTop = false;
        // Start once the list is laid out, so the cell is where it will stay.
        dragLayer.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        dragLayer.getViewTreeObserver().removeOnPreDrawListener(this);
                        startLanding(icon, host);
                        return true;
                    }
                });
    }

    private void startLanding(FolderIcon icon, ViewGroup host) {
        DragLayer dragLayer = mLauncher.getDragLayer();
        if (mLandingIcon != icon || mLandingHost != host || icon.getParent() != dragLayer) {
            return;
        }
        Rect hostRect = new Rect();
        dragLayer.getDescendantRectRelativeToSelf(host, hostRect);
        if (!host.isAttachedToWindow() || hostRect.isEmpty() || !Rect.intersects(hostRect,
                new Rect(0, 0, dragLayer.getWidth(), dragLayer.getHeight()))) {
            finishLanding();
            return;
        }
        BaseDragLayer.LayoutParams lp = (BaseDragLayer.LayoutParams) icon.getLayoutParams();
        float startX = icon.getTranslationX();
        float startY = icon.getTranslationY();
        cancelLandingAnimation();
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(LAND_ANIMATION_DURATION);
        anim.setInterpolator(Interpolators.EMPHASIZED);
        // Follows the cell every frame, in case the list still moves.
        anim.addUpdateListener(a -> {
            if (!host.isAttachedToWindow()) {
                return;
            }
            dragLayer.getDescendantRectRelativeToSelf(host, hostRect);
            float progress = (float) a.getAnimatedValue();
            icon.setTranslationX(startX + (hostRect.left - lp.x - startX) * progress);
            icon.setTranslationY(startY + (hostRect.top - lp.y - startY) * progress);
        });
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mLandingAnimator == animation) {
                    mLandingAnimator = null;
                }
                if (!mCancelled) {
                    finishLanding();
                }
            }
        });
        mLandingAnimator = anim;
        anim.start();
    }

    private void cancelLandingAnimation() {
        ValueAnimator anim = mLandingAnimator;
        mLandingAnimator = null;
        if (anim != null) {
            anim.cancel();
        }
    }

    private void onLandTimeout() {
        FolderIcon icon = mLandingIcon;
        if (icon == null || mLandingHost != null) {
            return;
        }
        mLandingIcon = null;
        if (icon.getParent() != mLauncher.getDragLayer()) {
            return;
        }
        icon.animate().cancel();
        icon.animate().alpha(0f).setDuration(LAND_ANIMATION_DURATION / 2)
                .withEndAction(() -> removeFromDragLayer(icon)).start();
    }

    private void finishLanding() {
        DragLayer dragLayer = mLauncher.getDragLayer();
        dragLayer.removeCallbacks(mLandTimeoutRunnable);
        cancelLandingAnimation();
        FolderIcon icon = mLandingIcon;
        ViewGroup host = mLandingHost;
        mLandingIcon = null;
        mLandingHost = null;
        if (icon == null || icon.getParent() != dragLayer) {
            return;
        }
        if (host != null && host.getTag() == icon.mInfo && host.isAttachedToWindow()
                && mIcons.get(icon.mInfo.id) == icon) {
            attachToHost(icon, host);
        } else {
            removeFromDragLayer(icon);
        }
    }

    private void removeFromDragLayer(FolderIcon icon) {
        if (icon == mLandingIcon) {
            cancelLandingAnimation();
        }
        icon.animate().cancel();
        if (icon.getParent() == mLauncher.getDragLayer()) {
            mLauncher.getDragLayer().removeView(icon);
        }
        icon.setTranslationX(0);
        icon.setTranslationY(0);
        icon.setAlpha(1f);
    }

    private void updateHover(DropTarget.DragObject d) {
        View target = null;
        FolderIcon folderIcon = null;
        AppInfo app = null;

        View child = findCellUnderDrag(d);
        FolderIcon childFolder = child != null ? getFolderIcon(child) : null;
        if (childFolder != null) {
            if (childFolder.acceptDrop(d.dragInfo)) {
                folderIcon = childFolder;
                target = child;
            }
        } else if (child instanceof BubbleTextView && child.getTag() instanceof AppInfo candidate
                && canBeInFolder(candidate) && !isDraggedItem(d, candidate)) {
            app = candidate;
            target = child;
        }

        if (target == mHoverView) {
            return;
        }
        clearHover();
        mHoverView = target;
        mHoverFolderIcon = folderIcon;
        mHoverApp = app;
        if (folderIcon != null) {
            folderIcon.onDrawerDragEnter(d.dragInfo);
        } else if (target instanceof BubbleTextView btv
                && btv.getParent() instanceof RecyclerView rv) {
            mCreateFeedback = new CreateFolderFeedback(rv, btv);
        }
    }

    private void clearHover() {
        if (mHoverFolderIcon != null) {
            mHoverFolderIcon.onDragExit();
        }
        if (mCreateFeedback != null) {
            mCreateFeedback.release();
            mCreateFeedback = null;
        }
        mHoverView = null;
        mHoverFolderIcon = null;
        mHoverApp = null;
    }

    /** Folder background growing under the hovered app, handed to the new folder on drop. */
    private final class CreateFolderFeedback extends RecyclerView.ItemDecoration {

        final RecyclerView mRecyclerView;
        final View mTarget;
        final PreviewBackground mBackground = new PreviewBackground(mLauncher);
        private boolean mReleased = false;
        private boolean mRemoved = false;

        CreateFolderFeedback(RecyclerView rv, BubbleTextView target) {
            mRecyclerView = rv;
            mTarget = target;
            target.getIconBounds(mTmpRect);
            mBackground.setupForAppDrawerIcon(mLauncher, mLauncher, rv, mTmpRect.left,
                    mTmpRect.top);
            mBackground.isClipping = false;
            mBackground.animateToAcceptInPlace();
            rv.addItemDecoration(this);
        }

        @Override
        public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent,
                @NonNull RecyclerView.State state) {
            if (mRemoved) {
                return;
            }
            if (mTarget.getParent() == parent) {
                int save = c.save();
                c.translate(mTarget.getLeft() + mTarget.getTranslationX(),
                        mTarget.getTop() + mTarget.getTranslationY());
                mBackground.drawUnderItem(c);
                c.restoreToCount(save);
            }
            if (mReleased && !mBackground.isScaleAnimationRunning()) {
                parent.post(this::remove);
            }
        }

        void release() {
            if (mReleased) {
                return;
            }
            mReleased = true;
            mBackground.animateToRest();
            mRecyclerView.invalidate();
        }

        void remove() {
            if (mRemoved) {
                return;
            }
            mRemoved = true;
            mRecyclerView.removeItemDecoration(this);
        }
    }

    private static boolean isDraggedItem(DropTarget.DragObject d, AppInfo candidate) {
        if (d.dragInfo == candidate) {
            return true;
        }
        ComponentKey dragged = d.dragInfo != null ? d.dragInfo.getComponentKey() : null;
        return dragged != null && dragged.equals(candidate.getComponentKey());
    }

    @Nullable
    private static FolderIcon getFolderIcon(View cell) {
        if (cell instanceof FolderIcon folderIcon) {
            return folderIcon;
        }
        if (cell instanceof ViewGroup group && group.getChildCount() == 1
                && group.getChildAt(0) instanceof FolderIcon folderIcon) {
            return folderIcon;
        }
        return null;
    }

    @Nullable
    private View findCellUnderDrag(DropTarget.DragObject d) {
        AllAppsRecyclerView rv = mLauncher.getAppsView().getActiveRecyclerView();
        if (rv == null || !rv.isShown()) {
            return null;
        }
        d.getVisualCenter(mTmpCenter);
        mTmpPoint[0] = Math.round(mTmpCenter[0]);
        mTmpPoint[1] = Math.round(mTmpCenter[1]);
        mLauncher.getDragLayer().mapCoordInSelfToDescendant(rv, mTmpPoint);

        View child = rv.findChildViewUnder(mTmpPoint[0], mTmpPoint[1]);
        if (child == null) {
            return null;
        }
        Rect iconBounds = mTmpRect;
        FolderIcon folderIcon = getFolderIcon(child);
        if (folderIcon != null) {
            folderIcon.getPreviewBounds(iconBounds);
            if (folderIcon != child) {
                iconBounds.offset(folderIcon.getLeft(), folderIcon.getTop());
            }
        } else if (child instanceof BubbleTextView btv) {
            btv.getIconBounds(iconBounds);
        } else {
            return null;
        }
        iconBounds.offset(Math.round(child.getLeft() + child.getTranslationX()),
                Math.round(child.getTop() + child.getTranslationY()));
        int slop = Math.round(iconBounds.width() * HOVER_SLOP_FRACTION);
        iconBounds.inset(-slop, -slop);
        return iconBounds.contains(mTmpPoint[0], mTmpPoint[1]) ? child : null;
    }

    private int getEdgeSize() {
        return Math.max(mLauncher.getDeviceProfile().getAllAppsProfile().getCellHeightPx() / 2,
                mLauncher.getResources().getDimensionPixelSize(R.dimen.drop_target_button_gap));
    }

    private void updateEdgeScroll(DropTarget.DragObject d) {
        ActivityAllAppsContainerView<?> apps = mLauncher.getAppsView();
        OneUiPagedAllAppsView pagedView = apps.getOneUiPagedView();
        if (pagedView != null) {
            updatePageFlip(pagedView, d);
            return;
        }
        AllAppsRecyclerView rv = apps.getActiveRecyclerView();
        if (rv == null || !rv.isShown()) {
            stopEdgeScroll();
            return;
        }
        mLauncher.getDragLayer().getDescendantRectRelativeToSelf(rv, mTmpRect);
        int edge = getEdgeSize();
        int top = mTmpRect.top + rv.getPaddingTop();
        int bottom = mTmpRect.bottom - rv.getPaddingBottom();
        int speed = 0;
        if (d.y < top + edge) {
            speed = -getScrollSpeed(top + edge - d.y, edge);
        } else if (d.y > bottom - edge) {
            speed = getScrollSpeed(d.y - (bottom - edge), edge);
        }
        if (speed == 0 || !rv.canScrollVertically(speed)) {
            stopEdgeScroll();
            return;
        }
        boolean running = mAutoScrollSpeed != 0;
        mAutoScrollSpeed = speed;
        if (!running) {
            mLauncher.getDragLayer().postOnAnimation(mAutoScroller);
        }
    }

    private int getScrollSpeed(int depth, int edge) {
        float progress = Utilities.boundToRange(depth / (float) edge, 0f, 1f);
        int maxSpeed = mLauncher.getDeviceProfile().getAllAppsProfile().getCellHeightPx() / 6;
        return Math.max(1, Math.round(maxSpeed * progress));
    }

    private void autoScrollStep() {
        DropTarget.DragObject d = mDragObject;
        AllAppsRecyclerView rv = mLauncher.getAppsView().getActiveRecyclerView();
        if (!mDragInDrawer || mAutoScrollSpeed == 0 || d == null || rv == null
                || !rv.canScrollVertically(mAutoScrollSpeed)) {
            mAutoScrollSpeed = 0;
            return;
        }
        rv.scrollBy(0, mAutoScrollSpeed);
        updateHover(d);
        mLauncher.getDragLayer().postOnAnimation(mAutoScroller);
    }

    private void updatePageFlip(OneUiPagedAllAppsView pagedView, DropTarget.DragObject d) {
        mLauncher.getDragLayer().getDescendantRectRelativeToSelf(pagedView, mTmpRect);
        int edge = getEdgeSize();
        int direction = 0;
        if (d.x < mTmpRect.left + edge) {
            direction = -1;
        } else if (d.x > mTmpRect.right - edge) {
            direction = 1;
        }
        if (Utilities.isRtl(mLauncher.getResources())) {
            direction = -direction;
        }
        int nextPage = pagedView.getNextPage() + direction;
        if (direction == 0 || nextPage < 0 || nextPage >= pagedView.getPageCount()) {
            mPageFlipDirection = 0;
            mPageFlipAlarm.cancelAlarm();
            return;
        }
        if (mPageFlipDirection != direction || !mPageFlipAlarm.alarmPending()) {
            mPageFlipDirection = direction;
            mPageFlipAlarm.setAlarm(PAGE_FLIP_DELAY);
        }
    }

    private void flipPage() {
        OneUiPagedAllAppsView pagedView = mLauncher.getAppsView().getOneUiPagedView();
        if (!mDragInDrawer || pagedView == null || mPageFlipDirection == 0) {
            return;
        }
        int nextPage = pagedView.getNextPage() + mPageFlipDirection;
        if (nextPage >= 0 && nextPage < pagedView.getPageCount()) {
            clearHover();
            pagedView.snapToPage(nextPage);
            mPageFlipAlarm.setAlarm(PAGE_FLIP_DELAY);
        }
    }

    private void stopEdgeScroll() {
        mAutoScrollSpeed = 0;
        mLauncher.getDragLayer().removeCallbacks(mAutoScroller);
        mPageFlipDirection = 0;
        mPageFlipAlarm.cancelAlarm();
    }

    public void onDestroy() {
        stopEdgeScroll();
        DragLayer dragLayer = mLauncher.getDragLayer();
        dragLayer.removeCallbacks(mLandRunnable);
        dragLayer.removeCallbacks(mLandTimeoutRunnable);
        mCreatingIcon = null;
        cancelLandingAnimation();
        mLandingIcon = null;
        mLandingHost = null;
        mCreateFeedback = null;
        mHoverView = null;
        mHoverFolderIcon = null;
        mHoverApp = null;
        mDragObject = null;
        mDragOptions = null;
        mIcons.clear();
        mFolders.clear();
    }

    @NonNull
    @Override
    public String toString() {
        return "DrawerFolderController{folders=" + mFolders.size() + "}";
    }
}
