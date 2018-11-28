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

import static com.android.launcher3.states.RotationHelper.ALLOW_ROTATION_PREFERENCE_KEY;
import static com.android.launcher3.states.RotationHelper.getAllowRotationDefaultValue;

import android.app.ActionBar;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.SwitchPreference;
import android.view.MenuItem;

import com.android.internal.util.bootleggers.BootlegUtils;

public class SettingsHomescreen extends SettingsActivity implements PreferenceFragment.OnPreferenceStartFragmentCallback {

    static final String KEY_FEED_INTEGRATION = "pref_feed_integration";
    static final String KEY_SHOW_QUICKSPACE = "pref_show_quickspace";

    @Override
    protected void onCreate(final Bundle bundle) {
        super.onCreate(bundle);
        if (bundle == null) {
            getFragmentManager().beginTransaction().replace(android.R.id.content, new HomescreenSettingsFragment()).commit();
        }
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

    public static class HomescreenSettingsFragment extends PreferenceFragment
            implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

        ActionBar actionBar;

        private Context mContext;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            addPreferencesFromResource(R.xml.home_screen_preferences);

            mContext = getActivity();

            actionBar=getActivity().getActionBar();
            assert actionBar != null;
            actionBar.setDisplayHomeAsUpEnabled(true);

            SwitchPreference feedIntegration = (SwitchPreference)
                    findPreference(KEY_FEED_INTEGRATION);

            SwitchPreference showQuickspace = (SwitchPreference)
                    findPreference(KEY_SHOW_QUICKSPACE);

            if (!BootlegUtils.isPackageInstalled(mContext, LauncherTab.SEARCH_PACKAGE)) {
                getPreferenceScreen().removePreference(feedIntegration);
            }

            // Setup allow rotation preference
            Preference rotationPref = findPreference(ALLOW_ROTATION_PREFERENCE_KEY);
            if (getResources().getBoolean(R.bool.allow_rotation)) {
                // Launcher supports rotation by default. No need to show this setting.
                getPreferenceScreen().removePreference(rotationPref);
            } else {
                // Initialize the UI once
                rotationPref.setDefaultValue(getAllowRotationDefaultValue());
            }

            final ListPreference gridColumns = (ListPreference) findPreference(Utilities.GRID_COLUMNS);
            gridColumns.setSummary(gridColumns.getEntry());
            gridColumns.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    int index = gridColumns.findIndexOfValue((String) newValue);
                    gridColumns.setSummary(gridColumns.getEntries()[index]);
                    LauncherAppState.getInstanceNoCreate().setNeedsRestart();
                    return true;
                }
            });

            final ListPreference gridRows = (ListPreference) findPreference(Utilities.GRID_ROWS);
            gridRows.setSummary(gridRows.getEntry());
            gridRows.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    int index = gridRows.findIndexOfValue((String) newValue);
                    gridRows.setSummary(gridRows.getEntries()[index]);
                    LauncherAppState.getInstanceNoCreate().setNeedsRestart();
                    return true;
                }
            });

            final ListPreference hotseatColumns = (ListPreference) findPreference(Utilities.HOTSEAT_ICONS);
            hotseatColumns.setSummary(hotseatColumns.getEntry());
            hotseatColumns.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    int index = hotseatColumns.findIndexOfValue((String) newValue);
                    hotseatColumns.setSummary(hotseatColumns.getEntries()[index]);
                    LauncherAppState.getInstanceNoCreate().setNeedsRestart();
                    return true;
                }
            });

            SwitchPreference desktopShowLabel = (SwitchPreference) findPreference(Utilities.DESKTOP_SHOW_LABEL);
            desktopShowLabel.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    LauncherAppState.getInstanceNoCreate().setNeedsRestart();
                    return true;
                }
            });

            showQuickspace.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    LauncherAppState.getInstanceNoCreate().setNeedsRestart();
                    return true;
                }
            });

            SwitchPreference showBottomSearchBar = (SwitchPreference) findPreference(Utilities.BOTTOM_SEARCH_BAR_KEY);
            showBottomSearchBar.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    LauncherAppState.getInstanceNoCreate().setNeedsRestart();
                    return true;
                }
            });
        
            ListPreference searchProvider = (ListPreference) findPreference(Utilities.SEARCH_PROVIDER_KEY);
            if (BootlegUtils.isPackageInstalled(mContext, LauncherTab.SEARCH_PACKAGE)) {
                getPreferenceScreen().removePreference(searchProvider);
            } else {
                searchProvider.setSummary(searchProvider.getEntry());
                searchProvider.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        int index = searchProvider.findIndexOfValue((String) newValue);
                        searchProvider.setSummary(searchProvider.getEntries()[index]);
                        LauncherAppState.getInstanceNoCreate().setNeedsRestart();
                        return true;
                    }
                });
            }

            ListPreference dateFormat = (ListPreference) findPreference(Utilities.DATE_FORMAT_KEY);
            dateFormat.setSummary(dateFormat.getEntry());
            dateFormat.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    int index = dateFormat.findIndexOfValue((String) newValue);
                    dateFormat.setSummary(dateFormat.getEntries()[index]);
                    LauncherAppState.getInstanceNoCreate().setNeedsRestart();
                    return true;
                }
            });
        }

        @Override
        public void onResume() {
            super.onResume();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
        }

        @Override
        public boolean onPreferenceChange(Preference preference, final Object newValue) {
            switch (preference.getKey()) {
            }
            return false;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            return false;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
