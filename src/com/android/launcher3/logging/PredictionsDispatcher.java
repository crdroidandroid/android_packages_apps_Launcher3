/*
 * Copyright (C) 2018 CypherOS
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

package com.android.launcher3.logging;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;

import com.android.launcher3.SettingsAppDrawer;
import com.android.launcher3.hidenprotect.HiddenAppsFilter;
import com.android.launcher3.util.ComponentKeyMapper;

import com.android.launcher3.AppFilter;
import com.android.launcher3.AppInfo;
import com.android.launcher3.R;
import com.android.launcher3.SettingsActivity;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.util.ComponentKey;

import com.android.quickstep.logging.UserEventDispatcherExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PredictionsDispatcher extends UserEventDispatcherExtension implements OnSharedPreferenceChangeListener {

    private static final String TAG = "PredictionsDispatcher";

    public static final int BOOST_ON_OPEN = 9;
    public static final Set<String> EMPTY_SET = new HashSet();
    public static final int MAX_PREDICTIONS = 10;
    public static final String[] WATCHED_APPS = new String[] {
        "com.android.contacts",
        "com.android.deskclock",
        "com.android.dialer",
        "com.android.documentsui",
        "com.android.gallery3d",
        "com.android.messaging",
        "com.android.settings",
        "com.google.android.apps.photos",
        "com.google.android.apps.maps",
        "com.google.android.deskclock",
        "com.google.android.gm",
        "com.google.android.youtube",
        "com.google.android.music",
        "com.google.android.calendar",
        "com.google.android.apps.genie.geniewidget",
        "com.google.android.videos",
        "com.google.android.apps.docs",
        "com.google.android.keep",
        "com.google.android.apps.plus",
        "com.facebook.katana",
        "com.facebook.orca",
        "com.spotify.music",
        "com.android.chrome",
        "com.instagram.android",
        "com.snapchat.android",
        "com.skype.raider",
        "com.twitter.android",
        "com.viber.voip",
        "com.amazon.mShop.android.shopping",
        "com.microsoft.office.word",
        "org.telegram.messenger",
        "com.discord"
    };
    public static final String PREDICTION_PREFIX = "pref_prediction_count_";
    public static final String PREDICTION_SET = "pref_prediction_set";
    public AppFilter mAppFilter;
    public Context mContext;
    public PackageManager mPackageManager;
    public SharedPreferences mPrefs;

    public PredictionsDispatcher(Context context) {
        super(context);
        mAppFilter = new HiddenAppsFilter(context);
        mContext = context;
        mPrefs = Utilities.getPrefs(context);
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        mPackageManager = context.getPackageManager();
    }

    public List<ComponentKeyMapper> getPredictedApps() {
        List<ComponentKeyMapper> list = new ArrayList();
        if (isPredictorEnabled()) {
            clearNonExistingComponents();
            List<String> predictionList = new ArrayList(getStringSetCopy());
            Collections.sort(predictionList, new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    return Integer.compare(getLaunchCount(o2), getLaunchCount(o1));
                }
            });

            for (String prediction : predictionList) {
                list.add(getComponentFromString(prediction));
            }

            if (list.size() < MAX_PREDICTIONS) {
                for (String watchedApps : WATCHED_APPS) {
                    Intent intent = mPackageManager.getLaunchIntentForPackage(watchedApps);
                    if (intent != null) {
                        ComponentName componentInfo = intent.getComponent();
                        String prediction = componentInfo.getPackageName() + '/' + componentInfo.getClassName();
                        if (!predictionList.contains(prediction)) {
                            list.add(new ComponentKeyMapper(mContext, new ComponentKey(componentInfo, Process.myUserHandle())));
                        }
                    }
                }
            }

            if (list.size() > MAX_PREDICTIONS) {
                list = list.subList(0, MAX_PREDICTIONS);
            }
        }
        Log.d(TAG, "Got predicted apps");
        return list;
    }

    @Override
    public void logAppLaunch(View view, Intent intent) {
        super.logAppLaunch(view, intent);
        if (isPredictorEnabled() && recursiveIsDrawer(view)) {
            clearNonExistingComponents();

            ComponentName componentInfo = intent.getComponent();
            String prediction = componentInfo.getPackageName() + '/' + componentInfo.getClassName();

            Set<String> predictionSet = getStringSetCopy();
            Editor edit = mPrefs.edit();

            if (predictionSet.contains(prediction)) {
                edit.putInt(PREDICTION_PREFIX + prediction, getLaunchCount(prediction) + BOOST_ON_OPEN);
            } else if (predictionSet.size() < MAX_PREDICTIONS || decayHasSpotFree(predictionSet, edit)) {
                predictionSet.add(prediction);
            }

            edit.putStringSet(PREDICTION_SET, predictionSet);
            edit.apply();
        }
    }

    private boolean decayHasSpotFree(Set<String> toDecay, Editor edit) {
        boolean spotFree = false;
        Set<String> toRemove = new HashSet<>();
        for (String prediction : toDecay) {
            int launchCount = getLaunchCount(prediction);
            if (launchCount > 0) {
                edit.putInt(PREDICTION_PREFIX + prediction, --launchCount);
            } else if (!spotFree) {
                edit.remove(PREDICTION_PREFIX + prediction);
                toRemove.add(prediction);
                spotFree = true;
            }
        }
        for (String prediction : toRemove) {
            toDecay.remove(prediction);
        }
        return spotFree;
    }

    private int getLaunchCount(String component) {
        return mPrefs.getInt(PREDICTION_PREFIX + component, 0);
    }

    private boolean recursiveIsDrawer(View view) {
        if (view != null) {
            ViewParent parent = view.getParent();
            while (parent != null) {
                if (parent instanceof AllAppsContainerView) {
                    return true;
                }
                parent = parent.getParent();
            }
        }
        return false;
    }

    private boolean isPredictorEnabled() {
        return Utilities.getPrefs(mContext).getBoolean(SettingsAppDrawer.KEY_APP_SUGGESTIONS, true);
    }
  
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (!isPredictorEnabled()) {
            Set<String> predictionSet = getStringSetCopy();

            Editor edit = mPrefs.edit();
            for (String prediction : predictionSet) {
                Log.i("Predictor", "Clearing " + prediction + " at " + getLaunchCount(prediction));
                edit.remove(PREDICTION_PREFIX + prediction);
            }
            edit.putStringSet(PREDICTION_SET, EMPTY_SET);
            edit.apply();
        }
    }

    private ComponentKeyMapper getComponentFromString(String str) {
        int index = str.indexOf('/');
        return new ComponentKeyMapper(mContext, new ComponentKey(new ComponentName(str.substring(0, index), str.substring(index + 1)), Process.myUserHandle()));
    }

    private void clearNonExistingComponents() {
        Set<String> originalSet = mPrefs.getStringSet(PREDICTION_SET, EMPTY_SET);
        Set<String> predictionSet = new HashSet<>(originalSet);
        Editor edit = mPrefs.edit();
        for (String prediction : originalSet) {
            try {
                mPackageManager.getPackageInfo(prediction.substring(0, prediction.indexOf('/')), 0);
            } catch (NameNotFoundException | NumberFormatException e) {
                predictionSet.remove(prediction);
                edit.remove(PREDICTION_PREFIX + prediction);
            }
        }
        edit.putStringSet(PREDICTION_SET, predictionSet);
        edit.apply();
        Log.d(TAG, "Components cleared!");
    }

    private Set<String> getStringSetCopy() {
        Set<String> set = new HashSet<>();
        set.addAll(mPrefs.getStringSet(PREDICTION_SET, EMPTY_SET));
        return set;
    }
}
