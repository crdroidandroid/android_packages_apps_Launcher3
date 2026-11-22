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

import androidx.graphics.shapes.RoundedPolygon
import java.util.Locale

/** How a corner joins its two edges. */
enum class CornerStyle(val key: String) {
    /** Circular arc. */
    ROUND("round"),
    /** Arc pulled toward the corner, giving a squircle-like curve. */
    SMOOTH("smooth"),
    /** Straight chamfer. */
    CUT("cut");

    companion object {
        fun fromKey(key: String): CornerStyle? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A single corner of an icon shape.
 *
 * @param size how far the corner reaches along each edge, from 0 (sharp) to 1 (half the edge)
 */
data class ShapeCorner(val style: CornerStyle, val size: Float)

/** Builds SVG path strings on the 100x100 viewport used by [ShapesProvider]. */
object ShapePathBuilder {

    private const val SIZE = 100f
    private const val HALF = SIZE / 2f

    /** Control point distance, as a fraction toward the corner, approximating a circular arc. */
    private const val ROUND_CONTROL = 0.5522848f
    /** Control point distance for [CornerStyle.SMOOTH], hugging the corner more tightly. */
    private const val SMOOTH_CONTROL = 0.8f

    /** Returns the path of a square whose corners are shaped as given. */
    fun fromCorners(
        topLeft: ShapeCorner,
        topRight: ShapeCorner,
        bottomRight: ShapeCorner,
        bottomLeft: ShapeCorner,
    ): String {
        val tl = topLeft.extent()
        val tr = topRight.extent()
        val br = bottomRight.extent()
        val bl = bottomLeft.extent()
        return PathStringBuilder()
            .apply {
                moveTo(tl, 0f)
                lineTo(SIZE - tr, 0f)
                corner(topRight.style, SIZE, 0f, SIZE, tr)
                lineTo(SIZE, SIZE - br)
                corner(bottomRight.style, SIZE, SIZE, SIZE - br, SIZE)
                lineTo(bl, SIZE)
                corner(bottomLeft.style, 0f, SIZE, 0f, SIZE - bl)
                lineTo(0f, tl)
                corner(topLeft.style, 0f, 0f, tl, 0f)
            }
            .close()
    }

    /** Returns the path of [polygon], scaled to fill the viewport. */
    fun fromPolygon(polygon: RoundedPolygon): String {
        val cubics = polygon.normalized().cubics
        val builder = PathStringBuilder()
        if (cubics.isEmpty()) return builder.close()
        builder.moveTo(cubics[0].anchor0X * SIZE, cubics[0].anchor0Y * SIZE)
        for (c in cubics) {
            builder.cubicTo(
                c.control0X * SIZE,
                c.control0Y * SIZE,
                c.control1X * SIZE,
                c.control1Y * SIZE,
                c.anchor1X * SIZE,
                c.anchor1Y * SIZE,
            )
        }
        return builder.close()
    }

    private fun ShapeCorner.extent() = size.coerceIn(0f, 1f) * HALF

    /** Accumulates path commands, skipping segments that would not move the pen. */
    private class PathStringBuilder {
        private val sb = StringBuilder()
        private var x = 0f
        private var y = 0f

        fun moveTo(x: Float, y: Float) {
            sb.append('M').append(fmt(x)).append(' ').append(fmt(y))
            this.x = x
            this.y = y
        }

        fun lineTo(x: Float, y: Float) {
            if (x == this.x && y == this.y) return
            sb.append(" L").append(fmt(x)).append(' ').append(fmt(y))
            this.x = x
            this.y = y
        }

        fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) {
            if (x == this.x && y == this.y && x1 == x && y1 == y && x2 == x && y2 == y) return
            sb.append(" C").append(fmt(x1)).append(' ').append(fmt(y1))
                .append(' ').append(fmt(x2)).append(' ').append(fmt(y2))
                .append(' ').append(fmt(x)).append(' ').append(fmt(y))
            this.x = x
            this.y = y
        }

        /**
         * Draws a corner from the current point, around ([cx], [cy]), ending at ([ex], [ey]).
         */
        fun corner(style: CornerStyle, cx: Float, cy: Float, ex: Float, ey: Float) {
            val sx = x
            val sy = y
            if (sx == ex && sy == ey) return
            val t =
                when (style) {
                    CornerStyle.CUT -> {
                        lineTo(ex, ey)
                        return
                    }
                    CornerStyle.ROUND -> ROUND_CONTROL
                    CornerStyle.SMOOTH -> SMOOTH_CONTROL
                }
            cubicTo(
                sx + (cx - sx) * t,
                sy + (cy - sy) * t,
                ex + (cx - ex) * t,
                ey + (cy - ey) * t,
                ex,
                ey,
            )
        }

        fun close(): String = sb.append(" Z").toString()

        private fun fmt(v: Float): String =
            String.format(Locale.US, "%.3f", v).trimEnd('0').trimEnd('.').ifEmpty { "0" }
    }
}
