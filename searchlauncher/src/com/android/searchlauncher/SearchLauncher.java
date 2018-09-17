package com.android.searchlauncher;

import com.android.launcher3.Launcher;

public class SearchLauncher extends Launcher {
    private static final String AOSP_FLAVOR = "aosp";
    private static final String GO_FLAVOR = "l3go";

    public SearchLauncher() {
        setLauncherCallbacks(new SearchLauncherCallbacks(this));
    }
}
