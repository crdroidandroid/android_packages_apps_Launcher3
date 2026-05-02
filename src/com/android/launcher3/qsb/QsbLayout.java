package com.android.launcher3.qsb;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.PaintDrawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Reorderable;
import com.android.launcher3.Utilities;
import com.android.launcher3.qsb.QsbContainerView;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.Themes;
import android.view.View;

public class QsbLayout extends FrameLayout implements Reorderable {

    private static final String TAG = "QsbLayout";

    private ImageView micIcon;
    private ImageView gIcon;
    private ImageView lensIcon;
    private ImageView geminiIcon;
    private FrameLayout inner;
    private FrameLayout outer;

    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);
    private float mScaleForReorderBounce = 1f;

    private boolean mIsThemed;
    private boolean mIsPixelStyle;

    public QsbLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public QsbLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        micIcon = findViewById(R.id.mic_icon);
        gIcon = findViewById(R.id.g_icon);
        lensIcon = findViewById(R.id.lens_icon);
        geminiIcon = findViewById(R.id.gemini_icon);
        inner = findViewById(R.id.inner);
        outer = findViewById(R.id.outer);

        mIsPixelStyle = outer != null;

        setUpMainSearch();
        setUpBackground();
        clipIconRipples();

        mIsThemed = LauncherPrefs.DOCK_THEME.get(getContext());

        setupGIcon();
        setupLensIcon();
        setupMicIcon();
        setupGeminiIcon();
    }

    private void clipIconRipples() {
        float cornerRadius = getCornerRadius();
        PaintDrawable pd = new PaintDrawable(Color.TRANSPARENT);
        pd.setCornerRadius(cornerRadius);
        micIcon.setClipToOutline(cornerRadius > 0);
        micIcon.setBackground(pd);
        lensIcon.setClipToOutline(cornerRadius > 0);
        lensIcon.setBackground(pd);
        gIcon.setClipToOutline(cornerRadius > 0);
        gIcon.setBackground(pd);
        if (!mIsPixelStyle) {
            geminiIcon.setClipToOutline(cornerRadius > 0);
            geminiIcon.setBackground(pd);
        }
    }

    private void setUpBackground() {
        float cornerRadius = getCornerRadius();
        int alphaValue = (LauncherPrefs.HOTSEAT_QSB_OPACITY.get(getContext()) * 255) / 100;
        int baseColor = Themes.getAttrColor(getContext(), R.attr.qsbFillColor);
        if (LauncherPrefs.DOCK_THEME.get(getContext()))
            baseColor = Themes.getAttrColor(getContext(), R.attr.qsbFillColorThemed);
        int color = (baseColor & 0x00FFFFFF) | (alphaValue << 24);
        float strokeWidth = LauncherPrefs.HOTSEAT_QSB_STROKE_WIDTH.get(getContext());

        PaintDrawable backgroundDrawable = new PaintDrawable(color);
        backgroundDrawable.setCornerRadius(cornerRadius);

        if (mIsPixelStyle) {
            setUpOuterBackground(strokeWidth);
            setUpGeminiCircleBackground(cornerRadius, color);
        }

        if (strokeWidth != 0f && !mIsPixelStyle) {
            PaintDrawable strokeDrawable = new PaintDrawable(Themes.getColorAccent(getContext()));
            strokeDrawable.getPaint().setStyle(Paint.Style.STROKE);
            strokeDrawable.getPaint().setStrokeWidth(strokeWidth);
            strokeDrawable.setCornerRadius(cornerRadius);
            LayerDrawable combinedDrawable = new LayerDrawable(new Drawable[]{backgroundDrawable, strokeDrawable});

            inner.setClipToOutline(cornerRadius > 0);
            inner.setBackground(combinedDrawable);
        } else {
            inner.setClipToOutline(cornerRadius > 0);
            inner.setBackground(backgroundDrawable);
        }
    }

    private void setUpOuterBackground(float strokeWidth) {
        if (outer == null) return;

        int alphaValue = (LauncherPrefs.HOTSEAT_QSB_OUTER_OPACITY.get(getContext()) * 255) / 100;
        float cornerRadius = getOuterCornerRadius();
        int baseColor = Themes.getAttrColor(getContext(), R.attr.qsbOuterColorThemed);
        int outerColor = (baseColor & 0x00FFFFFF) | (alphaValue << 24);

        PaintDrawable outerFill = new PaintDrawable(outerColor);
        outerFill.setCornerRadius(cornerRadius);

        if (strokeWidth != 0f) {
            PaintDrawable outerStroke = new PaintDrawable(Themes.getColorAccent(getContext()));
            outerStroke.getPaint().setStyle(Paint.Style.STROKE);
            outerStroke.getPaint().setStrokeWidth(strokeWidth);
            outerStroke.setCornerRadius(cornerRadius);

            LayerDrawable outerCombined = new LayerDrawable(new Drawable[]{outerFill, outerStroke});
            outer.setClipToOutline(cornerRadius > 0);
            outer.setBackground(outerCombined);
        } else {
            outer.setClipToOutline(cornerRadius > 0);
            outer.setBackground(outerFill);
        }
    }

    private void setUpGeminiCircleBackground(float cornerRadius, int innerColor) {
        if (geminiIcon == null) return;

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(cornerRadius);
        background.setColor(innerColor);
        geminiIcon.setBackground(background);
        geminiIcon.setClipToOutline(cornerRadius > 0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(width, height);

        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (child != null) {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        setOnClickListener(null);
        if (gIcon != null) gIcon.setOnClickListener(null);
        if (lensIcon != null) lensIcon.setOnClickListener(null);
        if (micIcon != null) micIcon.setOnClickListener(null);
        if (geminiIcon != null) geminiIcon.setOnClickListener(null);
        if (inner != null) inner.setBackground(null);
        if (outer != null) outer.setBackground(null);
    }

    private void setUpMainSearch() {
        setOnClickListener(view -> {
            try {
                Intent intent = new Intent();
                intent.setAction("android.search.action.GLOBAL_SEARCH");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.setPackage(QsbContainerView.getSearchWidgetPackageName(view.getContext()));
                view.getContext().startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Main search launch failed", e);
            }
        });
    }

    private void setupGIcon() {
        if (gIcon == null) return;

        gIcon.setImageResource(mIsThemed
                ? R.drawable.ic_super_g_themed
                : R.drawable.ic_super_g_color);

        gIcon.setOnClickListener(view -> {
            try {
                Intent intent = view.getContext().getPackageManager().getLaunchIntentForPackage(Utilities.GSA_PACKAGE);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    view.getContext().startActivity(intent);
                }
            } catch (Exception e) {
                Log.e(TAG, "Google icon launch failed", e);
            }
        });
    }

    private void setupLensIcon() {
        if (lensIcon == null) return;

        lensIcon.setImageResource(mIsThemed
                ? R.drawable.ic_lens_themed
                : R.drawable.ic_lens_color);

        lensIcon.setOnClickListener(view -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setComponent(new ComponentName(Utilities.GSA_PACKAGE, Utilities.LENS_ACTIVITY));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.setData(Uri.parse(Utilities.LENS_URI));
                intent.putExtra("LensHomescreenShortcut", true);
                view.getContext().startActivity(intent);
            } catch (Exception e) {
                lensIcon.setVisibility(View.GONE);
                Log.e(TAG, "Lens icon launch failed", e);
            }
        });
    }

    private void setupMicIcon() {
        if (micIcon == null) return;

        if (Utilities.isMusicSearchEnabled(getContext())) {
            micIcon.setImageResource(mIsThemed
                    ? R.drawable.ic_music_themed
                    : R.drawable.ic_music_color);
        } else {
            micIcon.setImageResource(mIsThemed
                    ? R.drawable.ic_mic_themed
                    : R.drawable.ic_mic_color);
        }

        micIcon.setOnClickListener(view -> {
            try {
                Intent intent = new Intent();
                if (Utilities.isMusicSearchEnabled(view.getContext())) {
                    intent.setAction("com.google.android.googlequicksearchbox.MUSIC_SEARCH");
                    intent.setPackage(QsbContainerView.getSearchWidgetPackageName(view.getContext()));
                } else {
                    intent.setAction("android.intent.action.VOICE_COMMAND");
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                view.getContext().startActivity(intent);
            } catch (Exception e) {
                micIcon.setVisibility(View.GONE);
                Log.e(TAG, "Mic icon launch failed", e);
            }
        });
    }

    private void setupGeminiIcon() {
        if (geminiIcon == null) return;

        if (!Utilities.isPackageInstalled(getContext(), Utilities.GEMINI_PACKAGE)) {
            geminiIcon.setVisibility(View.GONE);
            return;
        }

        geminiIcon.setVisibility(View.VISIBLE);
        geminiIcon.setImageResource(mIsThemed
                ? R.drawable.ic_gemini_themed
                : R.drawable.ic_gemini_color);

        geminiIcon.setOnClickListener(view -> {
            Context ctx = view.getContext();
            try {
                Intent intent = view.getContext().getPackageManager().getLaunchIntentForPackage(Utilities.GEMINI_PACKAGE);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    view.getContext().startActivity(intent);
                }
            } catch (Exception e) {
                geminiIcon.setVisibility(View.GONE);
                Log.e(TAG, "Gemini launch failed", e);
            }
        });
    }

    private float getCornerRadius() {
        Resources res = getContext().getResources();
        float qsbWidgetHeight = res.getDimension(R.dimen.qsb_widget_height);
        float qsbWidgetPadding = res.getDimension(R.dimen.qsb_widget_vertical_padding);
        float innerHeight = qsbWidgetHeight - 2 * qsbWidgetPadding;
        return (innerHeight / 2) * ((float)LauncherPrefs.SEARCH_RADIUS_SIZE.get(getContext()) / 100f);
    }

    private float getOuterCornerRadius() {
        Resources res = getContext().getResources();
        float qsbWidgetHeight = res.getDimension(R.dimen.qsb_widget_height);
        return (qsbWidgetHeight / 2) * ((float)LauncherPrefs.SEARCH_RADIUS_SIZE.get(getContext()) / 100f);
    }

    @Override
    public MultiTranslateDelegate getTranslateDelegate() {
        return mTranslateDelegate;
    }

    @Override
    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        super.setScaleX(scale);
        super.setScaleY(scale);
    }

    @Override
    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }
}
