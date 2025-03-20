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
package com.android.launcher3.allapps.search;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsGridAdapter;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.views.ActivityContext;

/**
 * Provides views for local search results.
 */
public class DefaultSearchAdapterProvider extends SearchAdapterProvider<ActivityContext> {
    private View mHighlightedView;

    private String defaultSearchPackage;

    public DefaultSearchAdapterProvider(ActivityContext launcher) {
        super(launcher);
    }

    @Override
    public void onBindView(AllAppsGridAdapter.ViewHolder holder, int position) {
        if (position == 0) {
            mHighlightedView = holder.itemView;
        }
    }

    @Override
    public boolean isViewSupported(int viewType) {
        return false;
    }

    @Override
    public AllAppsGridAdapter.ViewHolder onCreateViewHolder(LayoutInflater layoutInflater,
            ViewGroup parent, int viewType) {
        return null;
    }

    @Override
    public boolean launchHighlightedItem() {
        if (mHighlightedView instanceof BubbleTextView
                && mHighlightedView.getTag() instanceof ItemInfo) {
            ItemInfo itemInfo = (ItemInfo) mHighlightedView.getTag();
            return mLauncher.startActivitySafely(
                    mHighlightedView, itemInfo.getIntent(), itemInfo) != null;
        }
        return false;
    }

    private String getDefaultSearchPackage(Context context) {
        if (defaultSearchPackage == null) {
            try {
                String packageName = null;

                SearchManager searchManager =
                        context.getSystemService(SearchManager.class);

                if (searchManager != null && searchManager.getGlobalSearchActivity() != null) {
                    packageName =
                            searchManager.getGlobalSearchActivity().getPackageName();
                }

                if (packageName == null || packageName.isEmpty()) {
                    packageName = context.getResources()
                            .getString(R.string.fallback_search_package_name);
                }

                defaultSearchPackage =
                        packageName.isEmpty() ? null : packageName;

            } catch (IllegalStateException e) {
                String fallback = context.getResources()
                        .getString(R.string.fallback_search_package_name);

                defaultSearchPackage =
                        fallback.isEmpty() ? null : fallback;
            }
        }

        return defaultSearchPackage;
    }

    @Override
    public boolean performGoogleSearch(View view, String query) {
        String pkgName = getDefaultSearchPackage(view.getContext());
        if (pkgName != null && view instanceof View) {
            Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
            intent.setPackage(pkgName);
            intent.putExtra(SearchManager.QUERY, query);
            return mLauncher.startActivitySafely(view, intent, null) != null;
        }
        return false;
    }

    @Override
    public View getHighlightedItem() {
        return mHighlightedView;
    }

    @Override
    public void clearHighlightedItem() {
        mHighlightedView = null;
    }
}
