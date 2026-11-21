/*
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
package com.android.launcher3.quickspace;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.app.smartspace.SmartspaceAction;
import android.app.smartspace.SmartspaceConfig;
import android.app.smartspace.SmartspaceManager;
import android.app.smartspace.SmartspaceSession;
import android.app.smartspace.SmartspaceTarget;
import android.app.smartspace.uitemplatedata.BaseTemplateData;
import android.app.smartspace.uitemplatedata.TapAction;
import android.app.smartspace.uitemplatedata.Text;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.android.launcher3.Launcher;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmartspaceWeatherSource {

    private static final String TAG = "Launcher3:SmartspaceWeather";
    // adb shell setprop log.tag.SmartspaceWeather DEBUG
    private static final String DEBUG_TAG = "SmartspaceWeather";

    public interface Listener {
        /** Called on the main thread when the weather changes. */
        void onSmartspaceWeatherChanged();
    }

    private static final String UI_SURFACE = "home";
    private static final int TARGET_COUNT = 5;
    private static final int FEATURE_WEATHER = 1;
    private static final String EXTRA_DESCRIPTION = "description";

    // Optional sign (ASCII or Unicode minus), 1-3 digits in any script,
    // then a degree sign (optionally followed by C/F) or ℃ / ℉.
    private static final Pattern TEMP_PATTERN =
            Pattern.compile("[-−]?\\p{Nd}{1,3}\\s*(?:°\\s*[CFcf]?|℃|℉)");
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[,،、;|•·\\n]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\p{Nd}");

    private final Context mContext;
    private final Listener mListener;
    private final SmartspaceSession.OnTargetsAvailableListener mTargetsListener =
            this::onTargetsAvailable;

    private SmartspaceSession mSession;

    // Main thread state
    private Weather mWeather;

    private static final class Weather {
        final String temp;
        final String condition;
        final Bitmap icon;
        final PendingIntent pendingIntent;
        final Intent intent;

        Weather(String temp, String condition, Bitmap icon,
                PendingIntent pendingIntent, Intent intent) {
            this.temp = temp;
            this.condition = condition;
            this.icon = icon;
            this.pendingIntent = pendingIntent;
            this.intent = intent;
        }

        boolean sameAs(Weather o) {
            if (o == null) return false;
            final boolean sameIcon = icon == null
                    ? o.icon == null
                    : o.icon != null && (icon == o.icon || icon.sameAs(o.icon));
            return TextUtils.equals(temp, o.temp) && TextUtils.equals(condition, o.condition)
                    && sameIcon && pendingIntent == o.pendingIntent;
        }
    }

    public SmartspaceWeatherSource(Context context, Listener listener) {
        mContext = context;
        mListener = listener;
    }

    public boolean start() {
        if (mSession != null) return true;
        final SmartspaceManager manager = mContext.getSystemService(SmartspaceManager.class);
        if (manager == null) return false;
        try {
            final SmartspaceConfig config = new SmartspaceConfig.Builder(mContext, UI_SURFACE)
                    .setSmartspaceTargetCount(TARGET_COUNT)
                    .build();
            mSession = manager.createSmartspaceSession(config);
            mSession.addOnTargetsAvailableListener(UI_HELPER_EXECUTOR, mTargetsListener);
            mSession.requestSmartspaceUpdate();
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Smartspace session unavailable", t);
            closeSession();
            return false;
        }
    }

    public void stop() {
        closeSession();
        mWeather = null;
    }

    public void requestUpdate() {
        if (mSession == null) return;
        try {
            mSession.requestSmartspaceUpdate();
        } catch (Throwable t) {
            Log.w(TAG, "Smartspace update request failed", t);
        }
    }

    private void closeSession() {
        final SmartspaceSession session = mSession;
        mSession = null;
        if (session == null) return;
        try {
            session.removeOnTargetsAvailableListener(mTargetsListener);
            session.close();
        } catch (Throwable ignored) { }
    }

    public boolean hasWeather() {
        return mWeather != null;
    }

    public String getTemp() {
        return mWeather != null ? mWeather.temp : null;
    }

    public String getCondition() {
        return mWeather != null ? mWeather.condition : null;
    }

    public Bitmap getIcon() {
        return mWeather != null ? mWeather.icon : null;
    }

    public boolean launch(View view) {
        final Weather weather = mWeather;
        if (weather == null) return false;
        if (weather.pendingIntent != null) {
            try {
                final ActivityOptions options = ActivityOptions.makeBasic();
                options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS);
                weather.pendingIntent.send(mContext, 0, null, null, null, null,
                        options.toBundle());
                return true;
            } catch (PendingIntent.CanceledException e) {
                Log.w(TAG, "Weather pending intent was cancelled", e);
            }
        }
        if (weather.intent != null && canStart(weather.intent)) {
            final Intent intent = new Intent(weather.intent)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                return Launcher.getLauncher(view.getContext())
                        .startActivitySafely(view, intent, null) != null;
            } catch (Exception e) {
                Log.w(TAG, "Weather intent launch failed", e);
            }
        }
        return false;
    }

    private boolean canStart(Intent intent) {
        try {
            final ActivityInfo info =
                    intent.resolveActivityInfo(mContext.getPackageManager(), 0);
            if (info == null) return false;
            if (!info.exported && !mContext.getPackageName().equals(info.packageName)) {
                return false;
            }
            return info.permission == null || mContext.checkSelfPermission(info.permission)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    private void onTargetsAvailable(List<SmartspaceTarget> targets) {
        Weather weather = null;
        final boolean debug = Log.isLoggable(DEBUG_TAG, Log.DEBUG);
        if (debug) dumpTargets(targets);
        if (targets != null) {
            for (SmartspaceTarget target : targets) {
                if (target.getFeatureType() != FEATURE_WEATHER) continue;
                try {
                    weather = parse(target);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to parse weather target", e);
                }
                if (weather != null) break;
            }
        }
        final Weather result = weather;
        MAIN_EXECUTOR.execute(() -> {
            if (mSession == null) return;
            if (result == null ? mWeather == null : result.sameAs(mWeather)) return;
            mWeather = result;
            mListener.onSmartspaceWeatherChanged();
        });
    }

    private Weather parse(SmartspaceTarget target) {
        final SmartspaceAction header = target.getHeaderAction();
        final SmartspaceAction base = target.getBaseAction();
        final BaseTemplateData template = target.getTemplateData();
        final BaseTemplateData.SubItemInfo sub =
                template != null ? template.getSubtitleItem() : null;
        final android.app.smartspace.uitemplatedata.Icon subIcon =
                sub != null ? sub.getIcon() : null;
        final CharSequence subText = sub != null ? textOf(sub.getText()) : null;

        String temp = null;
        for (CharSequence source : new CharSequence[] {
                header != null ? header.getTitle() : null,
                header != null ? header.getSubtitle() : null,
                subText}) {
            temp = findTemp(source);
            if (temp != null) break;
        }
        if (temp == null) return null;

        String condition = null;
        for (CharSequence source : new CharSequence[] {
                extraString(header, EXTRA_DESCRIPTION),
                extraString(base, EXTRA_DESCRIPTION),
                header != null ? header.getContentDescription() : null,
                subIcon != null ? subIcon.getContentDescription() : null,
                header != null ? header.getSubtitle() : null,
                subText}) {
            condition = findCondition(source);
            if (condition != null) break;
        }

        Icon icon = header != null ? header.getIcon() : null;
        if (icon == null && subIcon != null) icon = subIcon.getIcon();

        PendingIntent pendingIntent = null;
        Intent intent = null;
        final TapAction tap = sub != null ? sub.getTapAction() : null;
        for (Object source : new Object[] {header, base, tap}) {
            if (source instanceof SmartspaceAction action) {
                pendingIntent = action.getPendingIntent();
                intent = action.getIntent();
            } else if (source instanceof TapAction action) {
                pendingIntent = action.getPendingIntent();
                intent = action.getIntent();
            }
            if (pendingIntent != null || intent != null) break;
        }

        return new Weather(temp, condition, toBitmap(icon), pendingIntent, intent);
    }

    private static void dumpTargets(List<SmartspaceTarget> targets) {
        Log.d(DEBUG_TAG, "targets: " + (targets == null ? "null" : targets.size()));
        if (targets == null) return;
        for (SmartspaceTarget t : targets) {
            Log.d(DEBUG_TAG, "target id=" + t.getSmartspaceTargetId()
                    + " feature=" + t.getFeatureType() + " score=" + t.getScore()
                    + " component=" + t.getComponentName());
            if (t.getFeatureType() != FEATURE_WEATHER) continue;
            dumpAction("  header", t.getHeaderAction());
            dumpAction("  base", t.getBaseAction());
            final BaseTemplateData td = t.getTemplateData();
            if (td == null) continue;
            Log.d(DEBUG_TAG, "  template type=" + td.getTemplateType());
            dumpSubItem("  primary", td.getPrimaryItem());
            dumpSubItem("  subtitle", td.getSubtitleItem());
            dumpSubItem("  subtitleSupplemental", td.getSubtitleSupplementalItem());
            dumpSubItem("  supplementalLine", td.getSupplementalLineItem());
        }
    }

    private static void dumpAction(String label, SmartspaceAction a) {
        if (a == null) return;
        Log.d(DEBUG_TAG, label + " title=" + a.getTitle() + " subtitle=" + a.getSubtitle()
                + " cd=" + a.getContentDescription() + " pi=" + a.getPendingIntent());
        dumpIntent(label + " intent", a.getIntent());
        dumpBundle(label + " extras", a.getExtras());
    }

    private static void dumpSubItem(String label, BaseTemplateData.SubItemInfo item) {
        if (item == null) return;
        final android.app.smartspace.uitemplatedata.Icon icon = item.getIcon();
        Log.d(DEBUG_TAG, label + " text=" + textOf(item.getText())
                + " iconCd=" + (icon != null ? icon.getContentDescription() : null));
        final TapAction tap = item.getTapAction();
        if (tap != null) {
            Log.d(DEBUG_TAG, label + " tap pi=" + tap.getPendingIntent());
            dumpIntent(label + " tap intent", tap.getIntent());
            dumpBundle(label + " tap extras", tap.getExtras());
        }
    }

    private static void dumpIntent(String label, Intent intent) {
        if (intent == null) return;
        Log.d(DEBUG_TAG, label + "=" + intent.toUri(Intent.URI_INTENT_SCHEME));
        dumpBundle(label + " extras", intent.getExtras());
    }

    private static void dumpBundle(String label, Bundle bundle) {
        if (bundle == null) return;
        try {
            for (String key : bundle.keySet()) {
                Log.d(DEBUG_TAG, label + " [" + key + "]=" + bundle.get(key));
            }
        } catch (Exception e) {
            Log.d(DEBUG_TAG, label + " unreadable: " + e);
        }
    }

    private static CharSequence extraString(SmartspaceAction action, String key) {
        if (action == null) return null;
        try {
            final Bundle extras = action.getExtras();
            return extras != null ? extras.getCharSequence(key) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static CharSequence textOf(Text text) {
        return text != null ? text.getText() : null;
    }

    private static String findTemp(CharSequence source) {
        if (TextUtils.isEmpty(source)) return null;
        final Matcher m = TEMP_PATTERN.matcher(source);
        return m.find() ? m.group().replaceAll("\\s+", "") : null;
    }

    private static String findCondition(CharSequence source) {
        if (TextUtils.isEmpty(source)) return null;
        final String stripped = TEMP_PATTERN.matcher(source).replaceAll("");
        for (String part : SEPARATOR_PATTERN.split(stripped)) {
            final String p = part.trim();
            if (p.isEmpty() || DIGIT_PATTERN.matcher(p).find()) continue;
            if (p.equalsIgnoreCase("weather") || p.equalsIgnoreCase("weather icon")) continue;
            return p;
        }
        return null;
    }

    private Bitmap toBitmap(Icon icon) {
        if (icon == null) return null;
        try {
            final Drawable d = icon.loadDrawable(mContext);
            if (d == null) return null;
            if (d instanceof BitmapDrawable bd && bd.getBitmap() != null) return bd.getBitmap();
            final int w = Math.max(1, d.getIntrinsicWidth());
            final int h = Math.max(1, d.getIntrinsicHeight());
            final Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            final Canvas canvas = new Canvas(bitmap);
            d.setBounds(0, 0, w, h);
            d.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            Log.w(TAG, "Failed to load weather icon", e);
            return null;
        }
    }
}
