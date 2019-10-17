package com.android.launcher3;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IconPack {
    /*
    Useful Links:
    https://github.com/teslacoil/Example_NovaTheme
    http://stackoverflow.com/questions/7205415/getting-resources-of-another-application
    http://stackoverflow.com/questions/3890012/how-to-access-string-resource-from-another-application
     */
    private String packageName;
    private Context mContext;
    private Map<String, String> mIconPackResources;
    private List<String> mIconBackStrings;
    private List<Drawable> mIconBackList;
    private Drawable mIconUpon, mIconMask;
    private Resources mLoadedIconPackResource;
    private float mIconScale;

    public IconPack(Context context, String packageName){
        this.packageName = packageName;
        mContext = context;
    }

    public void setIcons(Map<String, String> iconPackResources, List<String> iconBackStrings) {
        mIconPackResources = iconPackResources;
        mIconBackStrings = iconBackStrings;
        mIconBackList = new ArrayList<Drawable>();
        try {
            mLoadedIconPackResource = mContext.getPackageManager().getResourcesForApplication(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            // must never happen cause itys checked already in the provider
            return;
        }
        mIconMask = getDrawableForName(IconPackProvider.ICON_MASK_TAG);
        mIconUpon = getDrawableForName(IconPackProvider.ICON_UPON_TAG);
        for (int i = 0; i < mIconBackStrings.size(); i++) {
            String backIconString = mIconBackStrings.get(i);
            Drawable backIcon = getDrawableWithName(backIconString);
            if (backIcon != null) {
                mIconBackList.add(backIcon);
            }
        }
        String scale = mIconPackResources.get(IconPackProvider.ICON_SCALE_TAG);
        if (scale != null) {
            try {
                mIconScale = Float.valueOf(scale);
            } catch (NumberFormatException e) {
            }
        }
    }

    public Drawable getIcon(LauncherActivityInfo info, Drawable appIcon, CharSequence appLabel) {
        return getIcon(info.getComponentName(), appIcon, appLabel);
    }

    public Drawable getIcon(ActivityInfo info, Drawable appIcon, CharSequence appLabel) {
        return getIcon(new ComponentName(info.packageName, info.name), appIcon, appLabel);
    }

    public Drawable getIcon(ComponentName name, Drawable appIcon, CharSequence appLabel) {
        return getDrawable(name.flattenToString(), appIcon, appLabel);
    }

    public Drawable getIcon(String packageName, Drawable appIcon, CharSequence appLabel) {
        return getDrawable(packageName, appIcon, appLabel);
    }

    private Drawable getDrawable(String name, Drawable appIcon, CharSequence appLabel) {
        Drawable d = getDrawableForName(name);
        if (d == null && appIcon != null) {
            d = compose(name, appIcon, appLabel);
        }
        return d;
    }

    private Drawable getIconBackFor(CharSequence tag) {
        if (mIconBackList != null && mIconBackList.size() != 0) {
            if (mIconBackList.size() == 1) {
                return mIconBackList.get(0);
            }
            try {
                Drawable back = mIconBackList.get((tag.hashCode() & 0x7fffffff) % mIconBackList.size());
                return back;
            } catch (ArrayIndexOutOfBoundsException e) {
                return mIconBackList.get(0);
            }
        }
        return null;
    }

    private int getResourceIdForDrawable(String resource) {
        int resId = mLoadedIconPackResource.getIdentifier(resource, "drawable", packageName);
        return resId;
    }

    private Drawable getDrawableForName(String name) {
        String item = mIconPackResources.get(name);
        if (!TextUtils.isEmpty(item)) {
            int id = getResourceIdForDrawable(item);
            if (id != 0) {
                return mLoadedIconPackResource.getDrawable(id);
            }
        }
        return null;
    }

    private Drawable getDrawableWithName(String name) {
        int id = getResourceIdForDrawable(name);
        if (id != 0) {
            return mLoadedIconPackResource.getDrawable(id);
        }
        return null;
    }

    private BitmapDrawable getBitmapDrawable(Drawable image) {
        if (image instanceof BitmapDrawable) {
            return (BitmapDrawable) image;
        }
        final Canvas canvas = new Canvas();
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,
                Paint.FILTER_BITMAP_FLAG));

        Bitmap bmResult = Bitmap.createBitmap(image.getIntrinsicWidth(), image.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bmResult);
        image.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        image.draw(canvas);
        return new BitmapDrawable(mLoadedIconPackResource, bmResult);
    }

    private Drawable compose(String name, Drawable appIcon, CharSequence appLabel) {
        final Canvas canvas = new Canvas();
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,
                Paint.FILTER_BITMAP_FLAG));

        BitmapDrawable appIconBitmap = getBitmapDrawable(appIcon);
        int width = appIconBitmap.getBitmap().getWidth();
        int height = appIconBitmap.getBitmap().getHeight();
        float scale = mIconScale;

        Drawable iconBack = getIconBackFor(appLabel);
        if (iconBack == null && mIconMask == null && mIconUpon == null){
            scale = 1.0f;
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height,
                Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bitmap);

        int scaledWidth = (int) (width * scale);
        int scaledHeight = (int) (height * scale);
        if (scaledWidth != width || scaledHeight != height) {
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(appIconBitmap.getBitmap(), scaledWidth, scaledHeight, true);
            canvas.drawBitmap(scaledBitmap, (width - scaledWidth) / 2, (height - scaledHeight) / 2, null);
        } else {
            canvas.drawBitmap(appIconBitmap.getBitmap(), 0, 0, null);
        }
        if (mIconMask != null) {
            mIconMask.setBounds(0, 0, width, height);
            BitmapDrawable  b = getBitmapDrawable(mIconMask);
            b.getPaint().setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            b.draw(canvas);
        }
        if (iconBack != null) {
            iconBack.setBounds(0, 0, width, height);
            BitmapDrawable  b = getBitmapDrawable(iconBack);
            b.getPaint().setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));
            b.draw(canvas);
        }
        if (mIconUpon != null) {
            mIconUpon.setBounds(0, 0, width, height);
            mIconUpon.draw(canvas);
        }
        return new BitmapDrawable(mLoadedIconPackResource, bitmap);
    }
}
