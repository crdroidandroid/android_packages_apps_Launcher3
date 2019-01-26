/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceFragment.OnPreferenceStartFragmentCallback;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.preference.TwoStatePreference;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Adapter;
import android.widget.ListView;

import com.android.launcher3.graphics.IconShapeOverride;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.util.ListViewHighlighter;
import com.android.launcher3.util.SettingsObserver;
import com.android.launcher3.views.ButtonPreference;
import com.android.launcher3.R;
import com.android.launcher3.EmptySettingsActivity.EmptySettingsFragment;

import android.util.Log;
import android.view.MenuItem;
import com.android.launcher3.util.LooperExecutor;

import java.util.Objects;

/**
 * Settings activity for Launcher. Currently implements the following setting: Allow rotation
 */
public class SettingsAppDrawer extends SettingsActivity implements PreferenceFragment.OnPreferenceStartFragmentCallback {

    private static final String HIDDEN_APPS = "hidden_app";
    public static final String KEY_APP_SUGGESTIONS = "pref_app_suggestions";

    @Override
    protected PreferenceFragment getNewFragment() {
        return new AppDrawerSettingsFragment();
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment preferenceFragment, Preference preference) {
        Fragment instantiate = Fragment.instantiate(this, preference.getFragment(), preference.getExtras());
        if (instantiate instanceof DialogFragment) {
            ((DialogFragment) instantiate).show(getFragmentManager(), preference.getKey());
        } else {
            getFragmentManager().beginTransaction().replace(android.R.id.content, instantiate).addToBackStack(preference.getKey()).commit();
        }
        return true;
    }

    /**
     * This fragment shows the launcher preferences.
     */
    public static class AppDrawerSettingsFragment extends EmptySettingsFragment
            implements Preference.OnPreferenceChangeListener {

        ActionBar actionBar;

        private Context mContext;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            addPreferencesFromResource(R.xml.app_drawer_preferences);

            mContext = getActivity();

            ContentResolver resolver = getActivity().getContentResolver();

            actionBar=getActivity().getActionBar();
            assert actionBar != null;
            actionBar.setDisplayHomeAsUpEnabled(true);

            SwitchPreference allAppsShowLabel = (SwitchPreference) findPreference(Utilities.ALLAPPS_SHOW_LABEL);
            allAppsShowLabel.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    LauncherAppState.getInstanceNoCreate().setNeedsRestart();
                    return true;
                }
            });

            SwitchPreference allAppsLongLabels = (SwitchPreference) findPreference(
                    Utilities.PREF_ALLAPPS_LONG_LABELS);
            allAppsLongLabels.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    LauncherAppState.getInstanceNoCreate().setNeedsRestart();
                    return true;
                }
            });

            Preference hiddenApp = findPreference(Utilities.KEY_HIDDEN_APPS);
            hiddenApp.setOnPreferenceClickListener(
                preference -> {
                    startActivity(new Intent(getActivity(), HiddenAppsActivity.class));
                    return false;
            });

            ((SwitchPreference) findPreference(KEY_APP_SUGGESTIONS)).setOnPreferenceChangeListener(this);
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (!KEY_APP_SUGGESTIONS.equals(preference.getKey())) {
                return false;
            }
            if (((Boolean) newValue).booleanValue()) {
                return true;
            }
            SuggestionConfirmationFragment suggestionConfirmationFragment = new SuggestionConfirmationFragment();
            suggestionConfirmationFragment.setTargetFragment(this, 0);
            suggestionConfirmationFragment.show(getFragmentManager(), preference.getKey());
            return false;
        }

    }

    public static class SuggestionConfirmationFragment extends DialogFragment implements OnClickListener {
        public Dialog onCreateDialog(Bundle bundle) {
            return new Builder(getContext())
                .setTitle(R.string.title_disable_suggestions_prompt)
                .setMessage(R.string.msg_disable_suggestions_prompt)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.label_turn_off_suggestions, this)
                .create();
        }

        public void onClick(DialogInterface dialogInterface, int res) {
            if (getTargetFragment() instanceof PreferenceFragment) {
                Preference findPreference = ((PreferenceFragment) getTargetFragment()).findPreference(KEY_APP_SUGGESTIONS);
                if (findPreference instanceof TwoStatePreference) {
                    ((TwoStatePreference) findPreference).setChecked(false);
                }
            }
        }
    }

}
