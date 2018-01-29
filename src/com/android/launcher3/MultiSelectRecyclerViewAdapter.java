/*
 * Copyright (C) 2017 Paranoid Android
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

package com.android.launcher3;

import android.app.ActionBar;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.R;
import com.android.launcher3.SelectableAdapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


class MultiSelectRecyclerViewAdapter extends SelectableAdapter<MultiSelectRecyclerViewAdapter.ViewHolder> {

    private List<Packages> mPackages;
    private ItemClickListener mClickListener;
    private PackageManager mPackageManager;
    private final Set<String> mBlackList = new HashSet<>();

    MultiSelectRecyclerViewAdapter(Context context, List<ResolveInfo> resolveInfos, ItemClickListener clickListener) {
        mClickListener = clickListener;
        mPackageManager = context.getPackageManager();

        mBlackList.add("com.google.android.googlequicksearchbox");
        mBlackList.add("com.google.android.apps.wallpaper");
        mBlackList.add("com.google.android.launcher");
        mPackages = new ArrayList<>();
        for (int i = 0; i < resolveInfos.size(); i++) {
            ResolveInfo info = resolveInfos.get(i);
            String pn = info.activityInfo.packageName;
            if (!mBlackList.contains(pn)) {
                mPackages.add(new Packages(info, pn));
            }
        }
    }

    // Create new views
    @Override
    public MultiSelectRecyclerViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemLayoutView = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.hide_item, null);

        return new ViewHolder(itemLayoutView, mClickListener);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int position) {
        viewHolder.label.setText(mPackages.get(position).getLabel());
        viewHolder.icon.setImageDrawable(mPackages.get(position).getIcon());
        viewHolder.checkBox.setChecked(isSelected(mPackages.get(position).getPackageName()));
    }

    @Override
    public int getItemCount() {
        return mPackages.size();
    }

    private class Packages {
        private String mPackageName;
        private CharSequence mLabel;
        private Drawable mIcon;

        public Packages(ResolveInfo info, String packageName) {
            mPackageName = packageName;
            mLabel = info.loadLabel(mPackageManager);
            mIcon = info.loadIcon(mPackageManager);
        }

        public String getPackageName() {
            return mPackageName;
        }

        public CharSequence getLabel() {
            return mLabel;
        }

        public Drawable getIcon() {
            return mIcon;
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private TextView label;
        private ImageView icon;
        private CheckBox checkBox;
        private ItemClickListener listener;

        ViewHolder(View itemLayoutView, ItemClickListener listener) {
            super(itemLayoutView);

            this.listener = listener;

            label = (TextView) itemLayoutView.findViewById(R.id.label);
            icon = (ImageView) itemLayoutView.findViewById(R.id.icon);
            checkBox = (CheckBox) itemLayoutView.findViewById(R.id.check);

            itemLayoutView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (listener != null) {
                listener.onItemClicked(getAdapterPosition());
            }
        }
    }

    interface ItemClickListener {
        void onItemClicked(int position);
    }

    @Override
    void toggleSelection(ActionBar actionBar, int position) {
        String packageName = mPackages.get(position).getPackageName();
        if (mSelections.contains(packageName)) {
            mSelections.remove(packageName);
        } else {
            mSelections.add(packageName);
        }
        if (!mSelections.isEmpty()) {
            actionBar.setTitle(String.valueOf(mSelections.size()) + mContext.getString(R.string.hide_app_selected));
        } else {
            actionBar.setTitle(mContext.getString(R.string.hidden_app));
        }
        notifyItemChanged(position);
    }
}
