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
package com.android.launcher3.quickspace;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.util.Themes;

import com.android.launcher3.quickspace.QuickspaceController.OnDataListener;
import com.android.launcher3.quickspace.receivers.QuickSpaceActionReceiver;

public class QuickSpaceView extends FrameLayout implements OnDataListener {

    private static final String TAG = "Launcher3:QuickSpaceView";
    private static final boolean DEBUG = false;

    private static final int ANIMATE_IN_DURATION = 300;
    private static final int ANIMATE_OUT_DURATION = 400;
    private static final int CONTENT_FADE_DURATION = 200;

    public ColorStateList mColorStateList;
    public int mQuickspaceBackgroundRes;

    public ViewGroup mQuickspaceContent;
    public ImageView mEventSubIcon;
    public ImageView mNowPlayingIcon;
    public TextView mEventTitleSub;
    public TextView mEventTitleSubColored;
    public TextView mGreetingsExt;
    public TextView mGreetingsExtClock;
    public ViewGroup mWeatherContentSub;
    public ImageView mWeatherIconSub;
    public TextView mWeatherTempSub;
    public TextView mEventTitle;

    public boolean mIsQuickEvent;
    public boolean mWeatherAvailable;
    private boolean mFinishedInflate;
    private boolean mListenerRegistered;

    private boolean mIsAlternateStyle = false;
    private boolean mSkipAnimations = false;

    private final SparseArray<CharSequence> mMarqueeText = new SparseArray<>();

    public QuickspaceController mController;

    public QuickSpaceView(Context context, AttributeSet set) {
        super(context, set);
        if (!LauncherPrefs.SHOW_QUICKSPACE.get(context)) return;
        mController = new QuickspaceController(context);
        mColorStateList = ColorStateList.valueOf(Themes.getAttrColor(getContext(), R.attr.workspaceTextColor));
        mQuickspaceBackgroundRes = R.drawable.bg_quickspace;
        setClipChildren(false);
        setClipToPadding(false);
    }

    @Override
    public void onDataUpdated() {
        if (mController == null) return;
        boolean altUI = LauncherPrefs.SHOW_QUICKSPACE_ALT.get(getContext());
        if (mEventTitle == null || mIsAlternateStyle != altUI) {
            prepareLayout(altUI);
        }
        mIsQuickEvent = mController.isQuickEvent();
        mWeatherAvailable = mController.isWeatherAvailable();
        loadDoubleLine(altUI);
        mSkipAnimations = false;
    }

    private final void loadDoubleLine(boolean useAlternativeQuickspaceUI) {
        mEventTitle.setText(mController.getEventController().getTitle());
        if (useAlternativeQuickspaceUI) {
            String greetingsExt = mController.getEventController().getGreetings();
            if (mGreetingsExt != null) {
                if (greetingsExt != null && !greetingsExt.isEmpty()) {
                    mGreetingsExt.setVisibility(View.VISIBLE);
                    mGreetingsExt.setText(greetingsExt);
                    mGreetingsExt.setEllipsize(TruncateAt.END);
                    mGreetingsExt.setOnClickListener(mController.getEventController().getAction());
                } else {
                    mGreetingsExt.setVisibility(View.GONE);
                }
            }
            String greetingsExtClock = mController.getEventController().getClockExt();
            if (mGreetingsExtClock != null) {
                if (greetingsExtClock != null && !greetingsExtClock.isEmpty()) {
                    mGreetingsExtClock.setVisibility(View.VISIBLE);
                    mGreetingsExtClock.setText(greetingsExtClock);
                    mGreetingsExtClock.setOnClickListener(mController.getEventController().getAction());
                } else {
                    mGreetingsExtClock.setVisibility(View.GONE);
                }
            }
        }
        boolean shouldShowPsa = mIsQuickEvent && (LauncherPrefs.SHOW_QUICKSPACE_PSONALITY.get(getContext()) ||
                        mController.getEventController().isNowPlaying());
        if (shouldShowPsa) {
            maybeSetMarquee(mEventTitle);
            mEventTitle.setOnClickListener(mController.getEventController().getAction());
            mEventTitleSub.setText(mController.getEventController().getActionTitle());
            maybeSetMarquee(mEventTitleSub);
            mEventTitleSub.setOnClickListener(mController.getEventController().getAction());

            if (mEventTitleSub.getVisibility() != View.VISIBLE) {
                animateIn(mEventTitleSub);
            }

            if (useAlternativeQuickspaceUI) {
                if (mController.getEventController().isNowPlaying()) {
                    animateOut(mEventSubIcon);
                    animateIn(mEventTitleSubColored);
                    animateIn(mNowPlayingIcon);
                    mNowPlayingIcon.setOnClickListener(mController.getEventController().getAction());
                    mEventTitleSubColored.setText(getContext().getString(R.string.qe_now_playing_by));
                    mEventTitleSubColored.setOnClickListener(mController.getEventController().getAction());
                } else {
                    setEventSubIcon();
                    animateOut(mEventTitleSubColored);
                    animateOut(mNowPlayingIcon);
                }
            } else {
                setEventSubIcon();
            }
        } else {
            animateOut(mEventTitleSub);
            animateOut(mEventSubIcon);
            if (useAlternativeQuickspaceUI) {
                animateOut(mEventTitleSubColored);
                animateOut(mNowPlayingIcon);
            }
        }
        bindWeather(mWeatherContentSub, mWeatherTempSub, mWeatherIconSub);
    }

    private void maybeSetMarquee(final TextView tv) {
        if (tv == null) return;
        final CharSequence text = tv.getText();
        final int id = tv.getId();
        if (id != View.NO_ID && TextUtils.equals(mMarqueeText.get(id), text)) {
            return;
        }
        if (id != View.NO_ID) {
            mMarqueeText.put(id, text == null ? "" : text.toString());
        }
        tv.setSelected(false);
        tv.setEllipsize(TruncateAt.END);
        final float textWidth = tv.getPaint().measureText(text == null ? "" : text.toString());
        final ViewTreeObserver vto = tv.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                remove();
                if (!tv.isAttachedToWindow()) return;
                Layout layout = tv.getLayout();
                if (layout != null && layout.getEllipsizedWidth() < textWidth) {
                    tv.post(() -> {
                        if (!tv.isAttachedToWindow()) return;
                        tv.setEllipsize(TruncateAt.MARQUEE);
                        tv.setMarqueeRepeatLimit(1);
                        tv.setSelected(true);
                    });
                }
            }

            private void remove() {
                if (vto.isAlive()) {
                    vto.removeOnGlobalLayoutListener(this);
                } else {
                    tv.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            }
        });
    }

    private void setEventSubIcon() {
        Drawable icon = mController.getEventController().getActionIcon();
        if (icon != null) {
            if (mEventSubIcon.getVisibility() != View.VISIBLE) {
                animateIn(mEventSubIcon);
            }
            mEventSubIcon.setImageTintList(mController.getEventController().isNowPlaying() ? null : mColorStateList);
            mEventSubIcon.setImageDrawable(icon);
            mEventSubIcon.setOnClickListener(mController.getEventController().getAction());
        } else {
            animateOut(mEventSubIcon);
        }
    }

    private final void bindWeather(View container, TextView title, ImageView icon) {
        if (!mWeatherAvailable || mController.getEventController().isNowPlaying()) {
            container.setVisibility(View.GONE);
            return;
        }
        String weatherTemp = mController.getWeatherTemp();
        if (weatherTemp == null || weatherTemp.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }
        if (container.getVisibility() != View.VISIBLE) {
            animateIn(container);
        }
        container.setOnClickListener(QuickSpaceActionReceiver.getWeatherAction());
        title.setText(weatherTemp);
        title.setOnClickListener(QuickSpaceActionReceiver.getWeatherAction());
        Drawable d = mController.getWeatherIcon();
        icon.setImageDrawable(d);
        icon.setOnClickListener(QuickSpaceActionReceiver.getWeatherAction());
        icon.setVisibility(d != null ? View.VISIBLE : View.GONE);
    }

    private final void loadViews() {
        mEventTitle = (TextView) findViewById(R.id.quick_event_title);
        mEventTitleSub = (TextView) findViewById(R.id.quick_event_title_sub);
        mEventTitleSubColored = (TextView) findViewById(R.id.quick_event_title_sub_colored);
        mNowPlayingIcon = (ImageView) findViewById(R.id.now_playing_icon_sub);
        mEventSubIcon = (ImageView) findViewById(R.id.quick_event_icon_sub);
        mWeatherIconSub = (ImageView) findViewById(R.id.quick_event_weather_icon);
        mQuickspaceContent = (ViewGroup) findViewById(R.id.quickspace_content);
        mWeatherContentSub = (ViewGroup) findViewById(R.id.quick_event_weather_content);
        mWeatherTempSub = (TextView) findViewById(R.id.quick_event_weather_temp);
        if (LauncherPrefs.SHOW_QUICKSPACE_ALT.get(getContext())) {
            mGreetingsExtClock = (TextView) findViewById(R.id.extended_greetings_clock);
            mGreetingsExt = (TextView) findViewById(R.id.extended_greetings);
        } else {
            mGreetingsExtClock = null;
            mGreetingsExt = null;
        }
    }

    private void clearOldViewState() {
        mMarqueeText.clear();
        View[] vs = new View[]{ mQuickspaceContent, mEventTitle, mEventTitleSub, mEventTitleSubColored,
                mGreetingsExt, mGreetingsExtClock, mNowPlayingIcon, mEventSubIcon,
                mWeatherContentSub, mWeatherIconSub, mWeatherTempSub };
        for (View v : vs) if (v != null) {
            v.animate().cancel();
            v.setAlpha(1f);
            v.setTranslationY(0f);
            v.setOnClickListener(null);
            if (v instanceof ImageView) {
                ImageView iv = (ImageView) v;
                iv.setImageDrawable(null);
                iv.setImageBitmap(null);
                iv.setBackground(null);
            } else if (v instanceof TextView) {
                TextView tv = (TextView) v;
                tv.setSelected(false);
                tv.setEllipsize(null);
                tv.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                tv.setBackground(null);
            } else {
                v.setBackground(null);
            }
        }
    }

    private void prepareLayout(boolean alt) {
        mIsAlternateStyle = alt;
        mSkipAnimations = true;
        int insertIndex = (mQuickspaceContent != null) ? indexOfChild(mQuickspaceContent) : -1;
        if (mQuickspaceContent != null) {
            clearOldViewState();
            removeView(mQuickspaceContent);
        }
        addView(LayoutInflater.from(getContext()).inflate(
                alt ? R.layout.quickspace_alternate_double : R.layout.quickspace_doubleline,
                this, false),
                insertIndex < 0 ? -1 : insertIndex);

        loadViews();
        getQuickSpaceView();
        setBackgroundResource(mQuickspaceBackgroundRes);
    }

    private void getQuickSpaceView() {
        final View content = mQuickspaceContent;
        if (content == null) return;
        content.animate().cancel();
        if (content.getVisibility() == View.VISIBLE && content.getAlpha() == 1f) {
            return;
        }
        content.setVisibility(View.VISIBLE);
        if (!isShown()) {
            content.setAlpha(1f);
            return;
        }
        content.setAlpha(0f);
        content.animate()
            .alpha(1f)
            .setDuration(CONTENT_FADE_DURATION)
            .withEndAction(() -> content.setAlpha(1f))
            .start();
    }

    private static final Interpolator ANIMATE_IN = new DecelerateInterpolator();
    private static final Interpolator ANIMATE_OUT = new AccelerateInterpolator();

    private void animateIn(View view) {
        if (view == null) return;
        view.animate().cancel();
        if (view.getVisibility() == View.VISIBLE
                && view.getAlpha() == 1f
                && view.getTranslationY() == 0f) {
            return; // Already fully settled
        }
        if (mSkipAnimations || view.getHeight() == 0 || !isShown()) {
            view.setVisibility(View.VISIBLE);
            view.setAlpha(1f);
            view.setTranslationY(0f);
            return;
        }
        view.setVisibility(View.VISIBLE);
        view.setAlpha(0f);
        view.setTranslationY(view.getHeight() / 2f);
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(ANIMATE_IN_DURATION)
            .setInterpolator(ANIMATE_IN)
            .withEndAction(() -> {
                view.setAlpha(1f);
                view.setTranslationY(0f);
            })
            .start();
    }

    private void animateOut(View view) {
        if (view == null) return;
        view.animate().cancel();
        if (view.getVisibility() != View.VISIBLE) {
            view.setAlpha(1f);
            view.setTranslationY(0f);
            return;
        }
        if (mSkipAnimations || view.getHeight() == 0 || !isShown()) {
            view.setVisibility(View.GONE);
            view.setAlpha(1f);
            view.setTranslationY(0f);
            return;
        }
        view.animate()
            .alpha(0f)
            .translationY(view.getHeight() / 2f)
            .setDuration(ANIMATE_OUT_DURATION)
            .setInterpolator(ANIMATE_OUT)
            .withEndAction(() -> {
                view.setVisibility(View.GONE);
                view.setAlpha(1f);
                view.setTranslationY(0f);
            })
            .start();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mController != null && mFinishedInflate && !mListenerRegistered) {
            mListenerRegistered = true;
            mSkipAnimations = true;
            mController.addListener(this);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        if (mController == null) return;
        clearOldViewState();
        setBackground(null);
        super.onDetachedFromWindow();
        mController.onPause();
        mController.removeListener(this);
        mListenerRegistered = false;
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        if (mController == null) return;
        loadViews();
        mFinishedInflate = true;
        if (isAttachedToWindow() && !mListenerRegistered) {
            mSkipAnimations = true;
            mController.addListener(this);
            mListenerRegistered = true;
        }
    }

    public void onPause() {
        if (mController != null) mController.onPause();
    }

    public void onResume() {
        if (mController != null && mListenerRegistered) mController.onResume();
    }

    public void onDestroy() {
        if (mController == null) return;
        mController.onDestroy();
        mController = null;
        mMarqueeText.clear();
        mQuickspaceContent = null;
        mEventSubIcon = null;
        mNowPlayingIcon = null;
        mEventTitleSub = null;
        mEventTitleSubColored = null;
        mGreetingsExt = null;
        mGreetingsExtClock = null;
        mWeatherContentSub = null;
        mWeatherIconSub = null;
        mWeatherTempSub = null;
        mEventTitle = null;
    }

    public void setPadding(int n, int n2, int n3, int n4) {
        super.setPadding(0, 0, 0, 0);
    }
}
