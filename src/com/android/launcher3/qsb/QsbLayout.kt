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

package com.android.launcher3.qsb

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.PaintDrawable
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.Reorderable
import com.android.launcher3.Utilities
import com.android.launcher3.util.MultiTranslateDelegate
import com.android.launcher3.util.Themes

class QsbLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle), Reorderable {

    private var micIcon: ImageView? = null
    private var gIcon: ImageView? = null
    private var lensIcon: ImageView? = null
    private var geminiIcon: ImageView? = null
    private var inner: FrameLayout? = null
    private var outer: FrameLayout? = null

    private val mTranslateDelegate = MultiTranslateDelegate(this)
    private var mScaleForReorderBounce = 1f

    private var mIsThemed = false
    private var mIsPixelStyle = false

    private val defaultSearchPackage: String? by lazy {
        try {
            (context.getSystemService(SearchManager::class.java)?.globalSearchActivity?.packageName
                ?: context.resources.getString(R.string.fallback_search_package_name))
                .ifEmpty { null }
        } catch (e: IllegalStateException) {
            context.resources.getString(R.string.fallback_search_package_name).ifEmpty { null }
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        micIcon = findViewById(R.id.mic_icon)
        gIcon = findViewById(R.id.g_icon)
        lensIcon = findViewById(R.id.lens_icon)
        geminiIcon = findViewById(R.id.gemini_icon)
        inner = findViewById(R.id.inner)
        outer = findViewById(R.id.outer)

        mIsPixelStyle = outer != null

        setUpMainSearch()
        setUpBackground()
        clipIconRipples()

        mIsThemed = LauncherPrefs.DOCK_THEME.get(context)

        setupGIcon()
        setupLensIcon()
        setupMicIcon()
        setupGeminiIcon()
    }

    private fun clipIconRipples() {
        val cornerRadius = getCornerRadius()
        val pd = PaintDrawable(Color.TRANSPARENT)
        pd.setCornerRadius(cornerRadius)
        micIcon?.let {
            it.clipToOutline = cornerRadius > 0
            it.background = pd
        }
        lensIcon?.let {
            it.clipToOutline = cornerRadius > 0
            it.background = pd
        }
        gIcon?.let {
            it.clipToOutline = cornerRadius > 0
            it.background = pd
        }
        if (!mIsPixelStyle) {
            geminiIcon?.let {
                it.clipToOutline = cornerRadius > 0
                it.background = pd
            }
        }
    }

    private fun setUpBackground() {
        val cornerRadius = getCornerRadius()
        val alphaValue = LauncherPrefs.HOTSEAT_QSB_OPACITY.get(context) * 255 / 100
        var baseColor = Themes.getAttrColor(context, R.attr.qsbFillColor)
        if (LauncherPrefs.DOCK_THEME.get(context)) {
            baseColor = Themes.getAttrColor(context, R.attr.qsbFillColorThemed)
        }
        val color = (baseColor and 0x00FFFFFF) or (alphaValue shl 24)
        val strokeWidth = LauncherPrefs.HOTSEAT_QSB_STROKE_WIDTH.get(context).toFloat()

        val backgroundDrawable = PaintDrawable(color)
        backgroundDrawable.setCornerRadius(cornerRadius)

        if (mIsPixelStyle) {
            setUpOuterBackground(strokeWidth)
            setUpGeminiCircleBackground(cornerRadius, color)
        }

        if (strokeWidth != 0f && !mIsPixelStyle) {
            val strokeDrawable = PaintDrawable(Themes.getColorAccent(context))
            strokeDrawable.paint.style = Paint.Style.STROKE
            strokeDrawable.paint.strokeWidth = strokeWidth
            strokeDrawable.setCornerRadius(cornerRadius)
            val combinedDrawable = LayerDrawable(arrayOf<Drawable>(backgroundDrawable, strokeDrawable))

            inner?.clipToOutline = cornerRadius > 0
            inner?.background = combinedDrawable
        } else {
            inner?.clipToOutline = cornerRadius > 0
            inner?.background = backgroundDrawable
        }
    }

    private fun setUpOuterBackground(strokeWidth: Float) {
        val outer = outer ?: return

        val alphaValue = LauncherPrefs.HOTSEAT_QSB_OUTER_OPACITY.get(context) * 255 / 100
        val cornerRadius = getOuterCornerRadius()
        val baseColor = Themes.getAttrColor(context, R.attr.qsbOuterColorThemed)
        val outerColor = (baseColor and 0x00FFFFFF) or (alphaValue shl 24)

        val outerFill = PaintDrawable(outerColor)
        outerFill.setCornerRadius(cornerRadius)

        if (strokeWidth != 0f) {
            val outerStroke = PaintDrawable(Themes.getColorAccent(context))
            outerStroke.paint.style = Paint.Style.STROKE
            outerStroke.paint.strokeWidth = strokeWidth
            outerStroke.setCornerRadius(cornerRadius)

            val outerCombined = LayerDrawable(arrayOf<Drawable>(outerFill, outerStroke))
            outer.clipToOutline = cornerRadius > 0
            outer.background = outerCombined
        } else {
            outer.clipToOutline = cornerRadius > 0
            outer.background = outerFill
        }
    }

    private fun setUpGeminiCircleBackground(cornerRadius: Float, innerColor: Int) {
        val icon = geminiIcon ?: return

        val background = GradientDrawable()
        background.setShape(GradientDrawable.RECTANGLE)
        background.setCornerRadius(cornerRadius)
        background.setColor(innerColor)
        icon.background = background
        icon.clipToOutline = cornerRadius > 0
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        val height = View.MeasureSpec.getSize(heightMeasureSpec)

        setMeasuredDimension(width, height)

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child != null) {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        setOnClickListener(null)
        gIcon?.setOnClickListener(null)
        lensIcon?.setOnClickListener(null)
        micIcon?.setOnClickListener(null)
        geminiIcon?.setOnClickListener(null)
        inner?.background = null
        outer?.background = null
    }

    private fun setUpMainSearch() {
        setOnClickListener { view ->
            try {
                val intent = Intent().apply {
                    action = "android.search.action.GLOBAL_SEARCH"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    setPackage(defaultSearchPackage)
                }
                view.context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Main search launch failed", e)
            }
        }
    }

    private fun setupGIcon() {
        val icon = gIcon ?: return

        icon.setImageResource(
            if (mIsThemed) R.drawable.ic_super_g_themed else R.drawable.ic_super_g_color
        )

        icon.setOnClickListener { view ->
            try {
                val intent = view.context.packageManager
                    .getLaunchIntentForPackage(Utilities.GSA_PACKAGE)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    view.context.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Google icon launch failed", e)
            }
        }
    }

    private fun setupLensIcon() {
        val icon = lensIcon ?: return

        icon.setImageResource(
            if (mIsThemed) R.drawable.ic_lens_themed else R.drawable.ic_lens_color
        )

        icon.setOnClickListener { view ->
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    component = ComponentName(Utilities.GSA_PACKAGE, Utilities.LENS_ACTIVITY)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    data = Uri.parse(Utilities.LENS_URI)
                    putExtra("LensHomescreenShortcut", true)
                }
                view.context.startActivity(intent)
            } catch (e: Exception) {
                icon.visibility = View.GONE
                Log.e(TAG, "Lens icon launch failed", e)
            }
        }
    }

    private fun setupMicIcon() {
        val icon = micIcon ?: return

        if (Utilities.isMusicSearchEnabled(context)) {
            icon.setImageResource(
                if (mIsThemed) R.drawable.ic_music_themed else R.drawable.ic_music_color
            )
        } else {
            icon.setImageResource(
                if (mIsThemed) R.drawable.ic_mic_themed else R.drawable.ic_mic_color
            )
        }

        icon.setOnClickListener { view ->
            try {
                val intent = Intent()
                if (Utilities.isMusicSearchEnabled(view.context)) {
                    intent.action = "com.google.android.googlequicksearchbox.MUSIC_SEARCH"
                    intent.setPackage(Utilities.GSA_PACKAGE)
                } else {
                    intent.action = "android.intent.action.VOICE_COMMAND"
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                view.context.startActivity(intent)
            } catch (e: Exception) {
                icon.visibility = View.GONE
                Log.e(TAG, "Mic icon launch failed", e)
            }
        }
    }

    private fun setupGeminiIcon() {
        val icon = geminiIcon ?: return

        if (!Utilities.isPackageInstalled(context, Utilities.GEMINI_PACKAGE)) {
            icon.visibility = View.GONE
            return
        }

        icon.visibility = View.VISIBLE
        icon.setImageResource(
            if (mIsThemed) R.drawable.ic_gemini_themed else R.drawable.ic_gemini_color
        )

        icon.setOnClickListener { view ->
            try {
                val intent = view.context.packageManager
                    .getLaunchIntentForPackage(Utilities.GEMINI_PACKAGE)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    view.context.startActivity(intent)
                }
            } catch (e: Exception) {
                icon.visibility = View.GONE
                Log.e(TAG, "Gemini launch failed", e)
            }
        }
    }

    private fun getCornerRadius(): Float {
        val res = context.resources
        val qsbWidgetHeight = res.getDimension(R.dimen.qsb_widget_height)
        val qsbWidgetPadding = res.getDimension(R.dimen.qsb_widget_vertical_padding)
        val innerHeight = qsbWidgetHeight - 2 * qsbWidgetPadding
        return innerHeight / 2 * (LauncherPrefs.SEARCH_RADIUS_SIZE.get(context).toFloat() / 100f)
    }

    private fun getOuterCornerRadius(): Float {
        val res = context.resources
        val qsbWidgetHeight = res.getDimension(R.dimen.qsb_widget_height)
        return qsbWidgetHeight / 2 * (LauncherPrefs.SEARCH_RADIUS_SIZE.get(context).toFloat() / 100f)
    }

    override fun getTranslateDelegate(): MultiTranslateDelegate = mTranslateDelegate

    override fun setReorderBounceScale(scale: Float) {
        mScaleForReorderBounce = scale
        super.setScaleX(scale)
        super.setScaleY(scale)
    }

    override fun getReorderBounceScale(): Float = mScaleForReorderBounce

    companion object {
        private const val TAG = "QsbLayout"
    }
}
