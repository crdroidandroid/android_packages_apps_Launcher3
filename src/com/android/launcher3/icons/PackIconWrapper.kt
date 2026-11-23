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

package com.android.launcher3.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import androidx.annotation.WorkerThread
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object PackIconWrapper {

    private const val SAMPLE_SIZE = 64
    private const val SAMPLE_COUNT = 32
    /** Distance of the sampled ring from the center, relative to the icon size. */
    private const val SAMPLE_RADIUS = 0.42f
    private const val OPAQUE_ALPHA = 0xF0
    /** Share of edge samples that must agree on a color for it to be used. */
    private const val MIN_AGREEMENT = 0.6f
    private const val COLOR_BUCKET_SHIFT = 4

    private val LIGHT_BACKGROUND = Color.WHITE
    private val DARK_BACKGROUND = Color.rgb(0x1F, 0x1F, 0x1F)

    @JvmStatic
    @WorkerThread
    fun wrap(icon: Drawable): AdaptiveIconDrawable {
        val background = ColorDrawable(backgroundColor(icon))
        val extra = AdaptiveIconDrawable.getExtraInsetFraction()
        val inset = extra / (1 + 2 * extra)
        return AdaptiveIconDrawable(background, InsetDrawable(icon, inset))
    }

    private fun backgroundColor(icon: Drawable): Int {
        val bitmap = Bitmap.createBitmap(SAMPLE_SIZE, SAMPLE_SIZE, Bitmap.Config.ARGB_8888)
        val oldBounds = icon.copyBounds()
        icon.setBounds(0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
        icon.draw(Canvas(bitmap))
        icon.bounds = oldBounds

        val center = SAMPLE_SIZE / 2f
        val radius = SAMPLE_SIZE * SAMPLE_RADIUS
        val edgeColors =
            (0 until SAMPLE_COUNT)
                .map { i ->
                    val angle = 2 * Math.PI * i / SAMPLE_COUNT
                    val x = (center + radius * cos(angle)).roundToInt().coerceIn(0, SAMPLE_SIZE - 1)
                    val y = (center + radius * sin(angle)).roundToInt().coerceIn(0, SAMPLE_SIZE - 1)
                    bitmap.getPixel(x, y)
                }
                .filter { Color.alpha(it) >= OPAQUE_ALPHA }

        val color =
            (if (edgeColors.size * 2 >= SAMPLE_COUNT) agreedColor(edgeColors) else null)
                ?: contrastingBackground(bitmap)
        bitmap.recycle()
        return color
    }

    /** Returns the average of the most common edge color, if enough samples share it. */
    private fun agreedColor(colors: List<Int>): Int? {
        val bucket =
            colors.groupBy { bucketOf(it) }.maxByOrNull { it.value.size }?.value ?: return null
        if (bucket.size < colors.size * MIN_AGREEMENT) return null
        return Color.rgb(
            bucket.sumOf { Color.red(it) } / bucket.size,
            bucket.sumOf { Color.green(it) } / bucket.size,
            bucket.sumOf { Color.blue(it) } / bucket.size,
        )
    }

    private fun bucketOf(color: Int): Int =
        (Color.red(color) shr COLOR_BUCKET_SHIFT shl 8) or
            (Color.green(color) shr COLOR_BUCKET_SHIFT shl 4) or
            (Color.blue(color) shr COLOR_BUCKET_SHIFT)

    private fun contrastingBackground(bitmap: Bitmap): Int {
        var luminance = 0.0
        var weight = 0.0
        val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
        bitmap.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
        pixels.forEach {
            val alpha = Color.alpha(it) / 255.0
            if (alpha > 0) {
                luminance += ColorUtils.calculateLuminance(it or 0xFF000000.toInt()) * alpha
                weight += alpha
            }
        }
        val isLightContent = weight > 0 && luminance / weight > 0.5
        return if (isLightContent) DARK_BACKGROUND else LIGHT_BACKGROUND
    }
}
