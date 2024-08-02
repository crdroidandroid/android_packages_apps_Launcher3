/*
 * Copyright (C) 2023 The Android Open Source Project
 * Copyright (C) 2024 Neoteric OS
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
package com.android.quickstep.util;

import android.app.contextualsearch.ContextualSearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import com.android.launcher3.util.IntArray;
import com.android.quickstep.SystemUiProxy;

import static com.android.internal.app.AssistUtils.INVOCATION_TYPE_HOME_BUTTON_LONG_PRESS;

import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BOUNCER_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_QUICK_SETTINGS_EXPANDED;

/** Utilities to work with Assistant functionality. */
public class AssistUtils {

    private final String TAG = "AssistUtils";
    private boolean DEBUG = false;

    private Context mContext;
    private ContextualSearchManager mContextualSearchManager;
    private String mContextualSearchPkg;
    private int mContextualSearchDefValue;

    private static final long KEYGUARD_SHOWING_SYSUI_FLAGS = SYSUI_STATE_BOUNCER_SHOWING |
        SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING | SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED;
    private static final long SHADE_EXPANDED_SYSUI_FLAGS = SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED |
        SYSUI_STATE_QUICK_SETTINGS_EXPANDED;

    public AssistUtils(Context context) {
        mContext = context;
        mContextualSearchManager = (ContextualSearchManager) mContext.getSystemService(Context.CONTEXTUAL_SEARCH_SERVICE);
        mContextualSearchPkg = mContext.getResources()
                .getString(com.android.internal.R.string.config_defaultContextualSearchPackageName);
        mContextualSearchDefValue = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_searchAllEntrypointsEnabledDefault) ? 1 : 0;
    }

    /** Creates AssistUtils as specified by overrides */
    public static AssistUtils newInstance(Context context) {
        return new AssistUtils(context);
    }

    /** @return Array of AssistUtils.INVOCATION_TYPE_* that we want to handle instead of SysUI. */
    public int[] getSysUiAssistOverrideInvocationTypes() {
        if (Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.SEARCH_ALL_ENTRYPOINTS_ENABLED, mContextualSearchDefValue,
                UserHandle.USER_CURRENT) == 0 || !isContextualSearchIntentAvailable()) {
             return new int[0];
        }
        IntArray invocationTypes = new IntArray();
        invocationTypes.add(INVOCATION_TYPE_HOME_BUTTON_LONG_PRESS);
        int[] assistOverrideInvocationTypes = invocationTypes.toArray();
        return assistOverrideInvocationTypes;
    }

    /**
     * @return {@code true} if the override was handled, i.e. an assist surface was shown or the
     * request should be ignored. {@code false} means the caller should start assist another way.
     */
    public boolean tryStartAssistOverride(int invocationType) {
        if (invocationType != INVOCATION_TYPE_HOME_BUTTON_LONG_PRESS) {
            return false;
        }
        return invokeContextualSearch(ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME);
    }

    public boolean invokeContextualSearch(int invocationType) {
        if (!canDoContextualSearch()) {
            return false;
        }
        if (DEBUG) Log.d(TAG, "invokeContextualSearch: Contextual Search should start now");
        mContextualSearchManager.startContextualSearch(invocationType);
        return true;
    }

    public boolean canDoContextualSearch() {
        if (Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.SEARCH_ALL_ENTRYPOINTS_ENABLED, mContextualSearchDefValue,
                UserHandle.USER_CURRENT) == 0) {
            if (DEBUG) Log.d(TAG, "Contextual Search invocation failed: CTS setting disabled");
            return false;
        }

        if (mContextualSearchManager == null) {
            if (DEBUG) Log.d(TAG, "Contextual Search invocation failed: no ContextualSearchManager");
            return false;
        }

        boolean isNotificationShadeShowing = (SystemUiProxy.INSTANCE.get(mContext)
                .getLastSystemUiStateFlags() & SHADE_EXPANDED_SYSUI_FLAGS) != 0;
        if (isNotificationShadeShowing) {
            if (DEBUG) Log.d(TAG, "Contextual Search invocation failed: notification shade");
            return false;
        }

        boolean isKeyguardShowing = (SystemUiProxy.INSTANCE.get(mContext)
                .getLastSystemUiStateFlags() & KEYGUARD_SHOWING_SYSUI_FLAGS) != 0;
        if (isKeyguardShowing) {
            if (DEBUG) Log.d(TAG, "Contextual Search invocation failed: keyguard");
            return false;
        }

        if (!isContextualSearchIntentAvailable()) {
            if (DEBUG) Log.d(TAG, "Contextual Search invocation failed: Contextual Search intent not found");
            return false;
        }

        return true;
    }

    public boolean isContextualSearchIntentAvailable() {
        return !mContext.getPackageManager().queryIntentActivities
                 (new Intent(ContextualSearchManager.ACTION_LAUNCH_CONTEXTUAL_SEARCH).setPackage(mContextualSearchPkg),
                 PackageManager.MATCH_ALL).isEmpty();
    }
}
