package com.android.launcher3;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

class HomeKeyWatcher {

    private Context mContext;
    private IntentFilter mFilter;
    private OnHomePressedListener mListener;
    private HomeRecevier mRecevier;

    HomeKeyWatcher(Context context) {
        mContext = context;
        mRecevier = new HomeRecevier();
        mFilter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
    }

    void setOnHomePressedListener(OnHomePressedListener listener) {
        mListener = listener;
    }

    void startWatch() {
        mContext.registerReceiver(mRecevier, mFilter);
    }

    void stopWatch() {
        mListener = null;
        if (mRecevier != null) {
            try {
                mContext.unregisterReceiver(mRecevier);
                mRecevier = null;
            } catch (IllegalArgumentException e) {};
        }
    }

    class HomeRecevier extends BroadcastReceiver {
        final String SYSTEM_DIALOG_REASON_KEY = "reason";
        final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                String reason = intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY);
                if (reason != null) {
                    if (mListener != null) {
                        if (reason.equals(SYSTEM_DIALOG_REASON_HOME_KEY)) {
                            mListener.onHomePressed();
                        }
                    }
                }
            }
        }
    }

    public interface OnHomePressedListener {
        void onHomePressed();
    }
}
