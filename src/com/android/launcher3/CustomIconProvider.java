package com.android.launcher3;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.graphics.drawable.Drawable;

import androidx.annotation.Keep;

@Keep
public class CustomIconProvider extends IconProvider {

    private Context mContext;

    public CustomIconProvider(Context context) {
        mContext = context;
    }

    @Override
    public Drawable getIcon(LauncherActivityInfo info, int iconDpi, boolean flattenDrawable) {
        Drawable icon = super.getIcon(info, iconDpi, flattenDrawable);
        IconPack iconPack = IconPackProvider.loadAndGetIconPack(mContext);
        if (iconPack != null) {
            Drawable iconMask = iconPack.getIcon(info, null, info.getLabel());
            if (iconMask != null) {
                return iconMask;
            } else {
                return icon;
            }
        } else {
            return icon;
        }
    }
}
