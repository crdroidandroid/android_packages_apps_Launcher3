package com.android.launcher3.customization;

import android.app.ActivityOptions;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.Rect;

import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.widget.WidgetsBottomSheet;
import com.android.launcher3.util.PackageManagerHelper;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR;

public class InfoBottomSheet extends WidgetsBottomSheet {
    private final FragmentManager mFragmentManager;
    protected static Rect mSourceBounds;
    protected static Context mTarget;

    public InfoBottomSheet(Context context) {
        this(context, null);
    }

    public InfoBottomSheet(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InfoBottomSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mFragmentManager = Launcher.getLauncher(context).getFragmentManager();
    }

    public void configureBottomSheet(Rect sourceBounds, Context target) {
        mSourceBounds = sourceBounds;
        mTarget = target;
    }

    @Override
    public void populateAndShow(ItemInfo itemInfo) {
        super.populateAndShow(itemInfo);
        TextView title = findViewById(R.id.title);
        title.setText(itemInfo.title);

        PrefsFragment fragment =
                (PrefsFragment) mFragmentManager.findFragmentById(R.id.sheet_prefs);
        fragment.loadForApp(itemInfo);
    }

    @Override
    public void onDetachedFromWindow() {
        Fragment pf = mFragmentManager.findFragmentById(R.id.sheet_prefs);
        if (pf != null) {
            mFragmentManager.beginTransaction()
                    .remove(pf)
                    .commitAllowingStateLoss();
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void onWidgetsBound() {
    }

    public static class PrefsFragment extends PreferenceFragment
            implements Preference.OnPreferenceClickListener {
        private static final String KEY_SOURCE = "pref_app_info_source";
        private static final String KEY_LAST_UPDATE = "pref_app_info_last_update";
        private static final String KEY_VERSION = "pref_app_info_version";
        private static final String KEY_MORE = "pref_app_info_more";

        private Context mContext;

        private ItemInfo mItemInfo;

        private ComponentName mComponent;
        private ComponentKey mKey;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mContext = getActivity();
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.app_info_preferences);
        }

        @Override
        public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent,
                                                 Bundle savedInstanceState) {
            RecyclerView view = super.onCreateRecyclerView(inflater, parent, savedInstanceState);
            view.setOverScrollMode(View.OVER_SCROLL_NEVER);
            return view;
        }

        public void loadForApp(ItemInfo itemInfo) {
            mComponent = itemInfo.getTargetComponent();
            mItemInfo = itemInfo;
            mKey = new ComponentKey(mComponent, itemInfo.user);

            THREAD_POOL_EXECUTOR.execute(() -> {
                MetadataExtractor extractor = new MetadataExtractor(mContext, mComponent);

                CharSequence source = extractor.getSource();
                CharSequence lastUpdate = extractor.getLastUpdate();
                CharSequence version = mContext.getString(
                        R.string.app_info_version_value,
                        extractor.getVersionName(),
                        extractor.getVersionCode());

                MAIN_EXECUTOR.execute(() -> {
                    findPreference(KEY_SOURCE).setSummary(source);
                    findPreference(KEY_LAST_UPDATE).setSummary(lastUpdate);
                    findPreference(KEY_VERSION).setSummary(version);
                    findPreference(KEY_MORE).setOnPreferenceClickListener(this);
                });
            });
        }

        private void onMoreClick() {
            new PackageManagerHelper(InfoBottomSheet.mTarget).startDetailsActivityForInfo(
                        mItemInfo, InfoBottomSheet.mSourceBounds, ActivityOptions.makeBasic().toBundle());
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (KEY_MORE.equals(preference.getKey())) {
                  onMoreClick();
            }
            return false;
        }
    }
}
