package com.android.searchlauncher;

import com.android.launcher3.Launcher;
import com.android.launcher3.Launcher.LauncherOverlay;
import com.android.launcher3.Launcher.LauncherOverlayCallbacks;
import com.google.android.libraries.gsa.launcherclient.LauncherClient;
import com.google.android.libraries.gsa.launcherclient.LauncherClientCallbacks;

public class OverlayCallbackImpl implements LauncherOverlay, LauncherClientCallbacks {
    private LauncherClient mClient;
    private final Launcher mLauncher;
    private LauncherOverlayCallbacks mLauncherOverlayCallbacks;
    private boolean mWasOverlayAttached = false;

    public OverlayCallbackImpl(Launcher launcher) {
        mLauncher = launcher;
    }

    public void setClient(LauncherClient launcherClient) {
        mClient = launcherClient;
    }

    public void onServiceStateChanged(boolean z, boolean z2) {
        if (z != mWasOverlayAttached) {
            mWasOverlayAttached = z;
            mLauncher.setLauncherOverlay(z ? this : null);
        }
    }

    public void onOverlayScrollChanged(float f) {
        if (mLauncherOverlayCallbacks != null) {
            mLauncherOverlayCallbacks.onScrollChanged(f);
        }
    }

    public void onScrollInteractionBegin() {
        mClient.startMove();
    }

    public void onScrollInteractionEnd() {
        mClient.endMove();
    }

    public void onScrollChange(float f, boolean z) {
        mClient.updateMove(f);
    }

    public void setOverlayCallbacks(LauncherOverlayCallbacks launcherOverlayCallbacks) {
        mLauncherOverlayCallbacks = launcherOverlayCallbacks;
    }
}
