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

package com.android.launcher3.shapes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser
import com.android.launcher3.util.Themes
import kotlin.math.roundToInt

/**
 * Draws an outlined preview of an icon shape path defined on a 100x100 viewport. An empty
 * [pathString] previews the system adaptive icon mask.
 */
class IconShapePreviewDrawable(context: Context, pathString: String) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val size = (PREVIEW_SIZE_DP * density).roundToInt()
    private val strokeWidth = STROKE_WIDTH_DP * density

    private val basePath = createPath(pathString)
    private val drawPath = Path()
    private val matrix = Matrix()

    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color =
                ColorUtils.setAlphaComponent(
                    Themes.getAttrColor(context, android.R.attr.textColorPrimary),
                    FILL_ALPHA,
                )
        }

    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = this@IconShapePreviewDrawable.strokeWidth
            color = Themes.getAttrColor(context, android.R.attr.colorAccent)
        }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        drawPath.reset()
        if (bounds.isEmpty) return
        val inset = strokeWidth / 2f
        val dst = RectF(bounds).apply { inset(inset, inset) }
        matrix.setRectToRect(VIEWPORT, dst, Matrix.ScaleToFit.CENTER)
        basePath.transform(matrix, drawPath)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(drawPath, fillPaint)
        canvas.drawPath(drawPath, strokePaint)
    }

    override fun getIntrinsicWidth() = size

    override fun getIntrinsicHeight() = size

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = (FILL_ALPHA * alpha / 255f).roundToInt()
        strokePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        private const val TAG = "IconShapePreviewDrawable"
        private const val PATH_SIZE = 100
        private const val PREVIEW_SIZE_DP = 40
        private const val STROKE_WIDTH_DP = 1.5f
        private const val FILL_ALPHA = 0x1A

        private val VIEWPORT = RectF(0f, 0f, PATH_SIZE.toFloat(), PATH_SIZE.toFloat())

        private fun createPath(pathString: String): Path {
            if (pathString.isNotEmpty()) {
                try {
                    return PathParser.createPathFromPathData(pathString)
                } catch (e: RuntimeException) {
                    Log.e(TAG, "Unable to parse icon shape path", e)
                }
            }
            return AdaptiveIconDrawable(null, ColorDrawable(Color.BLACK)).run {
                setBounds(0, 0, PATH_SIZE, PATH_SIZE)
                Path(iconMask)
            }
        }
    }
}
