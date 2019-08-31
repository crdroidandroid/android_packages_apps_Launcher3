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
package com.android.launcher3.quickspace;

import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;

import java.util.List;
import java.util.Random;
import java.util.ArrayList;

public class QuickEventsController {

    private static final int SENSE_INFO_MAX_DURATION = 120000; // 2 minutes
    private static final String SETTING_DEVICE_INTRO_COMPLETED = "device_introduction_completed";
    private Context mContext;

    private String mEventTitle;
    private String mEventTitleSub;
    private OnClickListener mEventTitleSubAction = null;
    private int mEventSubIcon;

    private boolean mIsQuickEvent = false;
    private boolean mImportantQuickEvent = false;
    private boolean mRunning;

    // Device Intro
    private boolean mEventIntro = false;
    private boolean mIsFirstTimeDone; 
    // Now Playing
    private IntentFilter intent = new IntentFilter();
    private String[] intentWhitelist = new String[]{"com.android.music.metachanged", "com.android.music.playstatechanged", "com.android.music.playbackcomplete", "com.android.music.queuechanged", "com.jrtstudio.music.playstatechanged", "com.jrtstudio.music.playbackcomplete", "com.jrtstudio.music.metachanged", "com.htc.music.playstatechanged", "com.htc.music.playbackcomplete", "com.htc.music.metachanged", "fm.last.android.metachanged", "fm.last.android.playbackpaused", "fm.last.android.playbackcomplete", "com.lge.music.metachanged", "com.lge.music.playstatechanged", "com.lge.music.endofplayback", "com.miui.player.playbackcomplete", "com.miui.player.metachanged", "com.real.IMP.playstatechanged", "com.real.IMP.playbackcomplete", "com.real.IMP.metachanged", "com.sonyericsson.music.metachanged", "com.sonyericsson.music.playbackcontrol.ACTION_PLAYBACK_PAUSE", "com.sonyericsson.music.playbackcontrol.ACTION_PAUSED", "com.samsung.sec.android.MusicPlayer.playstatechanged", "com.samsung.sec.android.MusicPlayer.playbackcomplete", "com.samsung.sec.android.MusicPlayer.metachanged", "com.nullsoft.winamp.metachanged", "com.nullsoft.winamp.playstatechanged", "com.nullsoft.winamp.playbackcomplete", "com.amazon.mp3.metachanged", "com.amazon.mp3.playstatechanged", "com.amazon.mp3.playbackcomplete", "com.rdio.android.metachanged", "com.rdio.android.playbackcomplete", "com.rdio.android.playstatechanged", "com.spotify.music.metadatachanged", "com.spotify.music.playbackstatechanged", "com.spotify.music.queuechanged", "com.doubleTwist.androidPlayer.metachanged", "com.doubleTwist.androidPlayer.playstatechanged", "com.doubleTwist.androidPlayer.playbackcomplete", "org.iii.romulus.meridian.playbackcomplete", "org.iii.romulus.meridian.playstatechanged", "org.iii.romulus.meridian.metachanged", "com.tbig.playerpro.playstatechanged", "com.tbig.playerpro.metachanged", "com.tbig.playerpro.queuechanged", "com.tbig.playerpro.playbackcomplete"};
    private boolean mEventNowPlaying = false;
    private boolean mPixelNowPlaying = false;
    private boolean mLocalPlaying = false;
    private String mPrevArtist;
    private String mPrevSong;
    private String mPrevNowPlayingTrack;
    private String mArtist;
    private String mSong;
    private String mNowPlayingTrack;
    private BroadcastReceiver mNowPlayingListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String intentAction = intent.getAction();
            mArtist = intent.getStringExtra("artist");
            mSong = intent.getStringExtra("track");
            mLocalPlaying = intent.getBooleanExtra("playing", false);
            Log.d("Locally now Playing:", mSong + " by " + mArtist);
            if (intent == null || intent.getAction() == null) {
                return;
            }
            if (intentAction.contains("meta")) {
               initNowPlayingEvent();
            } else if (intentAction.toLowerCase().contains("play") || intentAction.toLowerCase().contains("pause") || intentAction.toLowerCase().contains("queue")) {
                initQuickEvents();
            }
        }
    };

    public QuickEventsController(Context context) {
        mContext = context;
        for (String musicint : intentWhitelist) {
            intent.addAction(musicint);
        }
        mRunning = true;
        context.registerReceiver(mNowPlayingListener, intent);
        initQuickEvents();
    }

    public void destroy() { 
        mContext.unregisterReceiver(mNowPlayingListener);  
    }

    public void initQuickEvents() {
        mIsFirstTimeDone = true;
        updateQuickEvents();
    }

    public void updateQuickEvents() {
        deviceIntroEvent();
        nowPlayingEvent();
        initNowPlayingEvent();
    }

    private void deviceIntroEvent() {
        if (!mRunning) return;

        if (mIsFirstTimeDone) {
            mEventIntro = false;
            return;
        }
        mIsQuickEvent = true;
        mEventIntro = true;
        mImportantQuickEvent = true;
        mEventTitle = mContext.getResources().getString(R.string.quick_event_rom_intro_welcome);
        String endingWelcome = mContext.getResources().getStringArray(R.array.welcome_message_variants)[getLuckyNumber(0,2)];
        mEventTitleSub = mContext.getResources().getString(R.string.quick_event_rom_intro_bridge, endingWelcome);

        mEventTitleSubAction = new OnClickListener() {
            @Override
            public void onClick(View view) {
                final Intent intent = new Intent(Intent.ACTION_MAIN).setClassName("com.android.settings","Settings$crDroidSettingsLayoutActivity")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                try {
                    Launcher.getLauncher(mContext).startActivitySafely(view, intent, null);
                } catch (ActivityNotFoundException ex) {
                }
                mIsQuickEvent = false;
            }
        };
    }

    public void nowPlayingEvent() {
        if (mEventNowPlaying) {
            boolean noWorkToDo = !isNowPlayingReady();
            if (noWorkToDo) {
                mIsQuickEvent = false;
                mEventNowPlaying = false;
                mImportantQuickEvent = false;
            }
        }
    }

    public void initNowPlayingEvent() {
        if (mEventIntro) return;

        if (!isNowPlayingReady()) return;

        if (Utilities.useAlternativeQuickspaceUI(mContext)) {
            mEventTitle = Utilities.formatDateTime(mContext, System.currentTimeMillis()) + " · " + mContext.getResources().getString(R.string.quick_event_ambient_now_playing);
        } else {
            mEventTitle = mContext.getResources().getString(R.string.quick_event_ambient_now_playing);
        }
        mEventSubIcon = R.drawable.ic_music_note_24dp;
        if (mNowPlayingTrack != mPrevNowPlayingTrack) {
            mEventTitleSub = mNowPlayingTrack;
            mPrevNowPlayingTrack = mNowPlayingTrack;
	        mIsQuickEvent = true;
	        mEventNowPlaying = true;
	        mImportantQuickEvent = false;
        } else if ((mArtist != null || mSong != null) && (mArtist != mPrevArtist && mSong != mPrevSong)) {
            mEventTitleSub = String.format(mContext.getResources().getString(
                    R.string.quick_event_ambient_song_artist), mSong, mArtist);
            mPrevSong = mSong;
            mPrevArtist = mArtist;
	        mIsQuickEvent = true;
	        mEventNowPlaying = true;
	        mImportantQuickEvent = false;
        }
        //mLastAmbientInfo = System.currentTimeMillis();

        mEventTitleSubAction = new OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Find a fun thing to do with the Info
                //String query = String.format(mContext.getResources().getString(
                //        R.string.quick_event_ambient_song_artist), entry.getSongTitle(), entry.getArtistTitle());
                //final Intent ambient = new Intent(Intent.ACTION_WEB_SEARCH)
                //        .putExtra(SearchManager.QUERY, query);
                //try {
                //    Launcher.getLauncher(mContext).startActivitySafely(view, ambient, null);
                //} catch (ActivityNotFoundException ex) {
                //}
            }
        };
    } 

    public boolean isNowPlayingReady() {
        boolean status = false;

        if (mLocalPlaying && mArtist != null && mSong != null) {
            status = true;
        } else {
            status = false;
        }
        return status;
    }

    public boolean isQuickEvent() {
        return mIsQuickEvent;
    }

    public boolean isQuickEventImportant() {
        return mImportantQuickEvent;
    }

    public String getTitle() {
        return mEventTitle;
    }

    public String getActionTitle() {
        return mEventTitleSub;
    }

    public OnClickListener getAction() {
        return mEventTitleSubAction;
    }

    public int getActionIcon() {
        return mEventSubIcon;
    }

    public int getLuckyNumber(int max) {
        return getLuckyNumber(0, max);
    }

    public int getLuckyNumber(int min, int max) {
        Random r = new Random();
        return r.nextInt((max - min) + 1) + min;
    }

    public void onPause() {
        mRunning = false;
    }

    public void onResume() {
        mRunning = true;
    }
}
