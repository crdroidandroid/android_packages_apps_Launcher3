package com.android.launcher3.icons;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.graphics.drawable.Drawable;

import androidx.annotation.Keep;

import com.android.launcher3.icons.IconPack;

@Keep
public class CustomIconProvider extends IconProvider {

    public CustomIconProvider(Context context) {
        super(context);
    }

    @Override
    public Drawable getIcon(LauncherActivityInfo info, int iconDpi) {
        Drawable icon = super.getIcon(info, iconDpi);
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
