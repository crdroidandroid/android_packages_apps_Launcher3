package com.android.searchlauncher;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import com.android.launcher3.AppInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherCallbacks;
import com.android.launcher3.Utilities;
import com.google.android.libraries.gsa.launcherclient.LauncherClient;
import com.google.android.libraries.gsa.launcherclient.LauncherClient.ClientOptions;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

public class SearchLauncherCallbacks implements LauncherCallbacks, OnSharedPreferenceChangeListener {
    private static final String KEY_ENABLE_APP_SUGGESTIONS = "pref_enable_app_suggestions";
    private static final String KEY_ENABLE_MINUS_ONE = "pref_enable_minus_one";
    public static final int MAX_NUM_APP_SUGGESTIONS = 5;
    private boolean mAlreadyOnHome;
    private final Launcher mLauncher;
    private LauncherClient mLauncherClient;
    private OverlayCallbackImpl mOverlayCallbacks;
    private boolean mResumed;
    private boolean mStarted;

    public void bindAllApplications(ArrayList<AppInfo> arrayList) {
    }

    public void dump(String str, FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
    }

    public boolean handleBackPressed() {
        return false;
    }

    public boolean hasSettings() {
        return true;
    }

    public void onActivityResult(int i, int i2, Intent intent) {
    }

    public void onLauncherProviderChange() {
    }

    public void onRequestPermissionsResult(int i, String[] strArr, int[] iArr) {
    }

    public void onSaveInstanceState(Bundle bundle) {
    }

    public void onTrimMemory(int i) {
    }

    public boolean startSearch(String str, boolean z, Bundle bundle) {
        return false;
    }

    public SearchLauncherCallbacks(Launcher launcher) {
        mLauncher = launcher;
    }

    public void onCreate(Bundle bundle) {
        SharedPreferences prefs = Utilities.getPrefs(mLauncher);
        mOverlayCallbacks = new OverlayCallbackImpl(mLauncher);
        mLauncherClient = new LauncherClient(mLauncher, mOverlayCallbacks, getClientOptions(prefs));
        mOverlayCallbacks.setClient(mLauncherClient);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    public void onDetachedFromWindow() {
        mLauncherClient.onDetachedFromWindow();
    }

    public void onAttachedToWindow() {
        mLauncherClient.onAttachedToWindow();
    }

    public void onHomeIntent(boolean z) {
        mLauncherClient.hideOverlay(mAlreadyOnHome);
    }

    public void onResume() {
        mResumed = true;
        if (mStarted) {
            mAlreadyOnHome = true;
        }
        mLauncherClient.onResume();
    }

    public void onPause() {
        mResumed = false;
        mLauncherClient.onPause();
    }

    public void onStart() {
        mStarted = true;
        mLauncherClient.onStart();
    }

    public void onStop() {
        mStarted = false;
        if (!mResumed) {
            mAlreadyOnHome = false;
        }
        mLauncherClient.onStop();
    }

    public void onDestroy() {
        mLauncherClient.onDestroy();
        Utilities.getPrefs(mLauncher).unregisterOnSharedPreferenceChangeListener(this);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
        if (KEY_ENABLE_MINUS_ONE.equals(str)) {
            mLauncherClient.setClientOptions(getClientOptions(sharedPreferences));
        }
    }

    private ClientOptions getClientOptions(SharedPreferences sharedPreferences) {
        return new ClientOptions(sharedPreferences.getBoolean(KEY_ENABLE_MINUS_ONE, true), true, true);
    }
}
