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

import com.android.launcher3.Launcher.LauncherOverlay;
import com.android.launcher3.Launcher.LauncherOverlayCallbacks;

import com.google.android.libraries.gsa.launcherclient.LauncherClient;
import com.google.android.libraries.gsa.launcherclient.ClientOptions;
import com.google.android.libraries.gsa.launcherclient.LauncherClientCallbacks;

public class LauncherTab {

    public static final String SEARCH_PACKAGE = "com.google.android.googlequicksearchbox";

    private Launcher mLauncher;

    private OverlayCallbackImpl mOverlayCallbacks;
    private LauncherClient mLauncherClient;

    private Workspace mWorkspace;

    public LauncherTab(Launcher launcher, boolean enabled) {
        mLauncher = launcher;
        mWorkspace = launcher.getWorkspace();

        updateLauncherTab(enabled);
    }

    protected void updateLauncherTab(boolean enabled) {
        mOverlayCallbacks = new OverlayCallbackImpl(mLauncher);
        mLauncherClient = new LauncherClient(mLauncher, mOverlayCallbacks, new ClientOptions(enabled ? 1 : 0));
        mOverlayCallbacks.setClient(mLauncherClient);
    }

    protected LauncherClient getClient() {
        return mLauncherClient;
    }
}
