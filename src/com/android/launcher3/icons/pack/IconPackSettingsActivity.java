/*
 * Copyright (C) 2020 Shift GmbH
 * Copyright (C) 2026 crDroid Android Project
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

package com.android.launcher3.icons.pack;

import static androidx.preference.PreferenceFragmentCompat.ARG_PREFERENCE_ROOT;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartFragmentCallback;
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartScreenCallback;
import androidx.preference.PreferenceScreen;

import com.android.launcher3.R;
import com.android.launcher3.util.PackageManagerHelper;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

public final class IconPackSettingsActivity extends CollapsingToolbarBaseActivity implements
        OnPreferenceStartFragmentCallback, OnPreferenceStartScreenCallback {

    public static final String EXTRA_FRAGMENT_ARGS = ":settings:fragment_args";

    // Intent extra to indicate the pref-key to highlighted when opening the settings activity
    public static final String EXTRA_FRAGMENT_HIGHLIGHT_KEY = ":settings:fragment_args_key";
    // Intent extra to indicate the pref-key of the root screen when opening the settings activity
    public static final String EXTRA_FRAGMENT_ROOT_KEY = ARG_PREFERENCE_ROOT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        Intent intent = getIntent();

        if (savedInstanceState == null) {
            Bundle args = intent.getBundleExtra(EXTRA_FRAGMENT_ARGS);
            if (args == null) {
                args = new Bundle();
            }

            String highlight = intent.getStringExtra(EXTRA_FRAGMENT_HIGHLIGHT_KEY);
            if (!TextUtils.isEmpty(highlight)) {
                args.putString(EXTRA_FRAGMENT_HIGHLIGHT_KEY, highlight);
            }
            String root = intent.getStringExtra(EXTRA_FRAGMENT_ROOT_KEY);
            if (!TextUtils.isEmpty(root)) {
                args.putString(EXTRA_FRAGMENT_ROOT_KEY, root);
            }

            final FragmentManager fm = getSupportFragmentManager();
            final Fragment f = fm.getFragmentFactory().instantiate(getClassLoader(),
                    getString(R.string.icon_pack_settings_class));
            f.setArguments(args);
            // Display the fragment as the main content.
            fm.beginTransaction().replace(
                com.android.settingslib.collapsingtoolbar.R.id.content_frame, f).commit();
        }
    }

    private boolean startPreference(String fragment, Bundle args, String key) {
        if (getSupportFragmentManager().isStateSaved()) {
            // Sometimes onClick can come after onPause because of being posted on the handler.
            // Skip starting new preferences in that case.
            return false;
        }
        final FragmentManager fm = getSupportFragmentManager();
        final Fragment f = fm.getFragmentFactory().instantiate(getClassLoader(), fragment);
        f.setArguments(args);
        // Display the fragment as the main content.
        fm.beginTransaction().replace(
            com.android.settingslib.collapsingtoolbar.R.id.content_frame, f).commit();

        return true;
    }

    @Override
    public boolean onPreferenceStartFragment(
            PreferenceFragmentCompat preferenceFragment, Preference pref) {
        return startPreference(pref.getFragment(), pref.getExtras(), pref.getKey());
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen pref) {
        Bundle args = new Bundle();
        args.putString(ARG_PREFERENCE_ROOT, pref.getKey());
        return startPreference(getString(R.string.icon_pack_title), args, pref.getKey());
    }

    @Override
    public boolean onNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_icon_pack, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.menu_icon_pack) {
            return openMarket();
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private boolean openMarket() {
        final String query = getString(R.string.icon_pack_title);
        final Intent intent = PackageManagerHelper.getMarketSearchIntent(this, query);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (intent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, R.string.icon_pack_no_market, Toast.LENGTH_LONG)
                .show();
            return false;
        }

        startActivity(intent);
        return true;
    }
}
