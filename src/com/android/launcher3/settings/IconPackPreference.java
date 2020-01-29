package com.android.launcher3.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.preference.ListPreference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.android.launcher3.IconPackProvider;
import com.android.launcher3.R;


public class IconPackPreference extends ListPreference {

    private final PackageManager pm;

    public IconPackPreference(Context context) {
        this(context, null);
    }

    public IconPackPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IconPackPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayoutResource(R.layout.preference_iconpack);
        pm = context.getPackageManager();
        init();
    }

    private void init() {
        String currentPack = IconPackProvider.getCurrentIconPack(getContext());
        if (currentPack.isEmpty()) {
            setNone();
        } else {
            try {
                ApplicationInfo info = pm.getApplicationInfo(currentPack, 0);
                setSummary(info.loadLabel(pm));
            } catch (PackageManager.NameNotFoundException e) {
                setNone();
                persistString("");
            }
        }
    }

    private void setNone() {
        setSummary("None");
    }

    @Override
    protected void onClick() {
        load();
        super.onClick();
    }

    protected void load() {
        final Map<String, IconPackInfo> packages = loadAvailableIconPacks();
        String[] packageNames = new String[packages.size()+1];
        CharSequence[] labels = new CharSequence[packages.size()+1];
        String defaultLabel = "None";
        packageNames[0] = "";
        labels[0] = defaultLabel;
        int i = 1;
        for (Map.Entry<String, IconPackInfo> entry : packages.entrySet()) {
            packageNames[i] = entry.getKey();
            labels[i] = entry.getValue().label;
            i++;
        }
        setEntries(labels);
        setEntryValues(packageNames);
    }

    private Map<String, IconPackInfo> loadAvailableIconPacks() {
        Map<String, IconPackInfo> iconPacks = new HashMap<>();
        List<ResolveInfo> list;
        list = pm.queryIntentActivities(new Intent("com.novalauncher.THEME"), 0);
        list.addAll(pm.queryIntentActivities(new Intent("org.adw.launcher.icons.ACTION_PICK_ICON"), 0));
        list.addAll(pm.queryIntentActivities(new Intent("com.dlto.atom.launcher.THEME"), 0));
        list.addAll(pm.queryIntentActivities(new Intent("android.intent.action.MAIN").addCategory("com.anddoes.launcher.THEME"), 0));
        for (ResolveInfo info : list) {
            iconPacks.put(info.activityInfo.packageName, new IconPackInfo(info, pm));
        }
        return iconPacks;
    }

    private static class IconPackInfo {
        String packageName;
        CharSequence label;

        IconPackInfo(ResolveInfo r, PackageManager packageManager) {
            packageName = r.activityInfo.packageName;
            label = r.loadLabel(packageManager);
        }

        public IconPackInfo(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }
}
