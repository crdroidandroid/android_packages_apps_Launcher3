/*
 * Copyright (C) 2018-2025 crDroid Android Project
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
package com.android.launcher3.quickspace.receivers;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.net.Uri;
import android.os.Process;
import android.os.UserHandle;
import android.provider.CalendarContract;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;

public class QuickSpaceActionReceiver {

    private static final OnClickListener calendarClick = v -> openGoogleCalendar(v);
    private static final OnClickListener weatherClick  = v -> openGoogleWeather(v);

    private static Launcher launcherFrom(View v) {
        return Launcher.getLauncher(v.getContext());
    }

    private static void openGoogleCalendar(View view) {
        Uri.Builder b = CalendarContract.CONTENT_URI.buildUpon().appendPath("time");
        ContentUris.appendId(b, System.currentTimeMillis());
        Uri uri = b.build();
        Intent i = new Intent(Intent.ACTION_VIEW)
            .setData(uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            launcherFrom(view).startActivitySafely(view, i, null);
        } catch (ActivityNotFoundException ex) {
            if (!Utilities.isGSAEnabled(view.getContext())) return;
            LauncherApps la = view.getContext()
                .getSystemService(LauncherApps.class);
            if (la != null) {
                la.startAppDetailsActivity(
                    new ComponentName("com.google.android.googlequicksearchbox", ""),
                    Process.myUserHandle(), null, null);
            }
        } catch (SecurityException ignored) { }
    }

    private static void openGoogleWeather(View view) {
        if (!Utilities.isGSAEnabled(view.getContext())) return;
        Intent i = new Intent(Intent.ACTION_VIEW)
            .setData(Uri.parse("dynact://velour/weather/ProxyActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            .setComponent(new ComponentName(
                "com.google.android.googlequicksearchbox",
                "com.google.android.apps.gsa.velour.DynamicActivityTrampoline"));
        try {
            launcherFrom(view).startActivitySafely(view, i, null);
        } catch (ActivityNotFoundException ex) {
            LauncherApps la = view.getContext()
                .getSystemService(LauncherApps.class);
            if (la != null) {
                la.startAppDetailsActivity(
                    new ComponentName("com.google.android.googlequicksearchbox",
                        "com.google.android.apps.gsa.velour.DynamicActivityTrampoline"),
                    Process.myUserHandle(), null, null);
            }
        } catch (SecurityException ignored) { }
    }

    public static OnClickListener getCalendarAction() { return calendarClick; }
    public static OnClickListener getWeatherAction()  { return weatherClick; }
}
