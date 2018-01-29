package com.android.launcher3;

import android.content.Context;
import android.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

public class StringSetAppFilter implements AppFilter {
    private final HashSet<String> mBlackList = new HashSet<>();


    public StringSetAppFilter(Context context) {
        mBlackList.add("com.google.android.googlequicksearchbox");
        mBlackList.add("com.google.android.apps.wallpaper");
        mBlackList.add("com.google.android.launcher");
    }

    @Override
    public boolean shouldShowApp(String packageName, Context context) {
        Set<String> hiddenApps = PreferenceManager.getDefaultSharedPreferences(context).getStringSet(Utilities.KEY_HIDDEN_APPS_SET, null);

        return !mBlackList.contains(packageName) && (hiddenApps == null || !hiddenApps.contains(packageName));
    }
}
