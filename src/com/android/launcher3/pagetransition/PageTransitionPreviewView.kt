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

package com.android.launcher3.pagetransition

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import com.android.app.animation.Interpolators
import com.android.launcher3.PageTransitionEffect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A small phone showing a home screen with a few pages over the wallpaper. It keeps swiping
 * back and forth between the pages using the selected [effect], the same transform the
 * workspace applies, and can also be swiped by hand.
 */
class PageTransitionPreviewView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** Effect played while the pages move. */
    var effect: PageTransitionEffect = PageTransitionEffect.NONE
        set(value) {
            if (field == value) return
            field = value
            invalidate()
            // Show the new effect right away instead of waiting for the next swipe.
            if (!isDragging) scheduleNextSwipe(EFFECT_CHANGE_DELAY_MS)
        }

    /** App icons placed on the pages and the hotseat, reused when there are too few. */
    var icons: List<Drawable> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    /** Wallpaper drawn behind the pages, cropped to the phone screen. */
    var wallpaper: Drawable? = null
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity

    private val screen = RectF()
    private val screenPath = Path()
    private val pages = RectF()
    private val hotseat = RectF()
    private var dotsY = 0f
    private var cellWidth = 0f
    private var cellHeight = 0f
    private var gridPadding = 0f
    private var iconSize = 0

    private val transform = PageTransitionEffect.PageTransform()
    private val matrix = Matrix()
    private val tmpMatrix = Matrix()
    private val camera = Camera()
    private val tmpRect = RectF()

    private val framePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = FRAME_STROKE_DP * density
            color = Color.BLACK
        }
    private val widgetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WIDGET_COLOR }
    private val widgetLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WIDGET_LINE_COLOR }
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PLACEHOLDER_COLOR }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    /** Current position, in pages. */
    private var scroll = 0f
    private var direction = 1
    private var animator: ValueAnimator? = null
    private val swipeRunnable = Runnable { swipeTo(nextPage(), SWIPE_DURATION_MS, true) }

    private var velocityTracker: VelocityTracker? = null
    private var downX = 0f
    private var downScroll = 0f
    private var isDragging = false

    private val isRtl
        get() = layoutDirection == LAYOUT_DIRECTION_RTL

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val metrics = resources.displayMetrics
        val aspect =
            min(metrics.widthPixels, metrics.heightPixels).toFloat() /
                max(metrics.widthPixels, metrics.heightPixels)
        val inset = SCREEN_INSET_DP * density
        val screenHeight = h - paddingTop - paddingBottom - 2 * inset
        val screenWidth = min(screenHeight * aspect, w - paddingLeft - paddingRight - 2 * inset)
        val left = paddingLeft + (w - paddingLeft - paddingRight - screenWidth) / 2f
        val top = paddingTop + inset
        screen.set(left, top, left + screenWidth, top + screenHeight)
        val radius = screenWidth * CORNER_RADIUS_FACTOR
        screenPath.reset()
        screenPath.addRoundRect(screen, radius, radius, Path.Direction.CW)

        hotseat.set(screen.left, screen.bottom - screenHeight * HOTSEAT_HEIGHT, screen.right,
            screen.bottom - screenHeight * NAV_BAR_HEIGHT)
        val dotsHeight = screenHeight * DOTS_HEIGHT
        dotsY = hotseat.top - dotsHeight / 2f
        pages.set(screen.left, screen.top + screenHeight * STATUS_BAR_HEIGHT, screen.right,
            hotseat.top - dotsHeight)

        gridPadding = screenWidth * GRID_PADDING
        cellWidth = (pages.width() - 2 * gridPadding) / COLUMNS
        cellHeight = pages.height() / ROWS
        iconSize = (min(cellWidth, cellHeight) * ICON_SCALE).roundToInt()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scheduleNextSwipe(START_DELAY_MS)
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (screen.isEmpty) return
        val saveCount = canvas.save()
        canvas.clipPath(screenPath)
        drawWallpaper(canvas)
        drawPages(canvas)
        drawPageDots(canvas)
        drawHotseat(canvas)
        canvas.restoreToCount(saveCount)
        val radius = screen.width() * CORNER_RADIUS_FACTOR
        canvas.drawRoundRect(screen, radius, radius, framePaint)
    }

    private fun drawWallpaper(canvas: Canvas) {
        val drawable = wallpaper
        if (drawable == null || drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            canvas.drawColor(FALLBACK_WALLPAPER_COLOR)
            return
        }
        // Center crop.
        val scale = max(screen.width() / drawable.intrinsicWidth,
            screen.height() / drawable.intrinsicHeight)
        val width = drawable.intrinsicWidth * scale
        val height = drawable.intrinsicHeight * scale
        val left = screen.centerX() - width / 2f
        val top = screen.centerY() - height / 2f
        drawable.setBounds(left.roundToInt(), top.roundToInt(), (left + width).roundToInt(),
            (top + height).roundToInt())
        drawable.draw(canvas)
    }

    private fun drawPages(canvas: Canvas) {
        val rtl = isRtl
        val pageWidth = pages.width()
        val pageHeight = pages.height()
        for (page in 0 until PAGE_COUNT) {
            // Positive while the page sits left of the screen center, like the workspace.
            val progress = if (rtl) page - scroll else scroll - page
            if (abs(progress) >= 1f) continue

            val saveCount = canvas.save()
            canvas.translate(pages.left - progress * pageWidth, pages.top)
            if (effect.getTransform(transform, progress, pageWidth, pageHeight, pageWidth, rtl)) {
                if (transform.alpha <= 0f) {
                    canvas.restoreToCount(saveCount)
                    continue
                }
                transform.getMatrix(matrix, tmpMatrix, camera, max(pageWidth, pageHeight))
                canvas.concat(matrix)
                if (transform.hasClip) {
                    canvas.clipRect(transform.clipLeft, 0f, transform.clipRight, pageHeight)
                }
                if (transform.alpha < 1f) {
                    canvas.saveLayerAlpha(0f, 0f, pageWidth, pageHeight,
                        (transform.alpha * 255).roundToInt())
                }
            }
            drawPageContent(canvas, page)
            canvas.restoreToCount(saveCount)
        }
    }

    /** Draws the items of [page] in page coordinates. */
    private fun drawPageContent(canvas: Canvas, page: Int) {
        val rtl = isRtl
        for (item in PAGE_ITEMS[page]) {
            val column = if (rtl) COLUMNS - item.column - item.spanX else item.column
            val left = gridPadding + column * cellWidth
            val top = item.row * cellHeight
            tmpRect.set(left, top, left + item.spanX * cellWidth, top + item.spanY * cellHeight)
            if (item.iconIndex < 0) {
                drawWidget(canvas, tmpRect)
            } else {
                drawIcon(canvas, item.iconIndex, tmpRect.centerX(), tmpRect.centerY())
            }
        }
    }

    private fun drawWidget(canvas: Canvas, cell: RectF) {
        cell.inset(cellWidth * WIDGET_INSET, cellHeight * WIDGET_INSET)
        val radius = min(cell.width(), cell.height()) * WIDGET_RADIUS
        canvas.drawRoundRect(cell, radius, radius, widgetPaint)
        // Two lines hinting at the widget content.
        val lineHeight = cell.height() * 0.12f
        val left = cell.left + cell.width() * 0.1f
        var top = cell.top + cell.height() * 0.28f
        canvas.drawRoundRect(left, top, left + cell.width() * 0.55f, top + lineHeight,
            lineHeight / 2f, lineHeight / 2f, widgetLinePaint)
        top += lineHeight * 2.2f
        canvas.drawRoundRect(left, top, left + cell.width() * 0.35f, top + lineHeight,
            lineHeight / 2f, lineHeight / 2f, widgetLinePaint)
    }

    private fun drawIcon(canvas: Canvas, index: Int, centerX: Float, centerY: Float) {
        val half = iconSize / 2f
        val icon = if (icons.isEmpty()) null else icons[index % icons.size]
        if (icon == null) {
            canvas.drawCircle(centerX, centerY, half * 0.9f, placeholderPaint)
            return
        }
        icon.setBounds((centerX - half).roundToInt(), (centerY - half).roundToInt(),
            (centerX + half).roundToInt(), (centerY + half).roundToInt())
        icon.draw(canvas)
    }

    private fun drawPageDots(canvas: Canvas) {
        val radius = screen.width() * DOT_RADIUS
        val spacing = radius * 4f
        val start = screen.centerX() - spacing * (PAGE_COUNT - 1) / 2f
        for (page in 0 until PAGE_COUNT) {
            val slot = if (isRtl) PAGE_COUNT - 1 - page else page
            val active = max(0f, 1f - abs(scroll - page))
            dotPaint.alpha = ((DOT_ALPHA + (1f - DOT_ALPHA) * active) * 255).roundToInt()
            canvas.drawCircle(start + slot * spacing, dotsY, radius, dotPaint)
        }
    }

    private fun drawHotseat(canvas: Canvas) {
        val cell = (hotseat.width() - 2 * gridPadding) / COLUMNS
        for (column in 0 until COLUMNS) {
            val slot = if (isRtl) COLUMNS - 1 - column else column
            drawIcon(canvas, HOTSEAT_FIRST_ICON + column,
                hotseat.left + gridPadding + (slot + 0.5f) * cell, hotseat.centerY())
        }
    }

    private fun nextPage(): Int {
        val current = scroll.roundToInt()
        if (current + direction !in 0 until PAGE_COUNT) direction = -direction
        return current + direction
    }

    private fun scheduleNextSwipe(delay: Long) {
        stopAnimation()
        if (isAttachedToWindow) postDelayed(swipeRunnable, delay)
    }

    private fun stopAnimation() {
        removeCallbacks(swipeRunnable)
        animator?.cancel()
        animator = null
    }

    private fun swipeTo(page: Int, duration: Long, keepLooping: Boolean) {
        stopAnimation()
        animator =
            ValueAnimator.ofFloat(scroll, page.toFloat()).apply {
                this.duration = duration
                interpolator = Interpolators.FAST_OUT_SLOW_IN
                addUpdateListener {
                    scroll = it.animatedValue as Float
                    invalidate()
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        private var canceled = false

                        override fun onAnimationCancel(animation: Animator) {
                            canceled = true
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            if (canceled) return
                            animator = null
                            scheduleNextSwipe(if (keepLooping) HOLD_MS else RESUME_DELAY_MS)
                        }
                    }
                )
                start()
            }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!screen.contains(event.x, event.y)) return false
                stopAnimation()
                downX = event.x
                downScroll = scroll
                isDragging = false
                velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.x - downX
                if (!isDragging && abs(dx) > touchSlop) isDragging = true
                if (isDragging) {
                    val sign = if (isRtl) 1f else -1f
                    scroll = (downScroll + sign * dx / pages.width())
                        .coerceIn(0f, (PAGE_COUNT - 1).toFloat())
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val tracker = velocityTracker
                tracker?.addMovement(event)
                tracker?.computeCurrentVelocity(1000)
                val velocity = (tracker?.xVelocity ?: 0f) * if (isRtl) -1f else 1f
                tracker?.recycle()
                velocityTracker = null
                if (event.actionMasked == MotionEvent.ACTION_UP && !isDragging) performClick()
                isDragging = false

                // Settle like the workspace: a fling moves on, otherwise snap to the nearest.
                val target =
                    when {
                        velocity < -minFlingVelocity -> ceil(scroll)
                        velocity > minFlingVelocity -> floor(scroll)
                        else -> scroll.roundToInt().toFloat()
                    }.toInt().coerceIn(0, PAGE_COUNT - 1)
                if (target != downScroll.roundToInt()) {
                    direction = if (target > downScroll) 1 else -1
                }
                swipeTo(target, SETTLE_DURATION_MS, false)
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    /** Item on a preview page; widgets have no icon. */
    private class Item(
        val column: Int,
        val row: Int,
        val spanX: Int = 1,
        val spanY: Int = 1,
        val iconIndex: Int = -1,
    )

    companion object {
        private const val PAGE_COUNT = 3
        private const val COLUMNS = 4
        private const val ROWS = 5

        private val PAGE_ITEMS: List<List<Item>> = buildPages()
        private val HOTSEAT_FIRST_ICON = PAGE_ITEMS.sumOf { page -> page.count { it.iconIndex >= 0 } }

        /** Number of app icons the preview can show without repeating. */
        @JvmField val ICON_COUNT = HOTSEAT_FIRST_ICON + COLUMNS

        private const val START_DELAY_MS = 600L
        private const val EFFECT_CHANGE_DELAY_MS = 250L
        private const val HOLD_MS = 1100L
        private const val RESUME_DELAY_MS = 2000L
        private const val SWIPE_DURATION_MS = 750L
        private const val SETTLE_DURATION_MS = 350L

        private const val SCREEN_INSET_DP = 4
        private const val FRAME_STROKE_DP = 3
        private const val CORNER_RADIUS_FACTOR = 0.1f
        private const val STATUS_BAR_HEIGHT = 0.07f
        private const val HOTSEAT_HEIGHT = 0.17f
        private const val NAV_BAR_HEIGHT = 0.03f
        private const val DOTS_HEIGHT = 0.05f
        private const val GRID_PADDING = 0.04f
        private const val ICON_SCALE = 0.68f
        private const val WIDGET_INSET = 0.12f
        private const val WIDGET_RADIUS = 0.2f
        private const val DOT_RADIUS = 0.012f
        private const val DOT_ALPHA = 0.4f

        private const val FALLBACK_WALLPAPER_COLOR = 0xFF37474F.toInt()
        private const val WIDGET_COLOR = 0x59FFFFFF
        private const val WIDGET_LINE_COLOR = 0x99FFFFFF.toInt()
        private const val PLACEHOLDER_COLOR = 0x80FFFFFF.toInt()

        /** Lays out three distinct pages, numbering the icons in order. */
        private fun buildPages(): List<List<Item>> {
            var icon = 0
            fun icons(row: Int, columns: IntRange) = columns.map { Item(it, row, iconIndex = icon++) }
            return listOf(
                // A wide widget above a row of icons.
                listOf(Item(0, 0, spanX = 4, spanY = 2)) + icons(4, 0..3),
                // A page full of icons.
                icons(0, 0..3) + icons(1, 0..3) + icons(2, 0..3),
                // A square widget next to icons.
                listOf(Item(0, 0, spanX = 2, spanY = 2)) + icons(0, 2..3) + icons(1, 2..3) +
                    icons(3, 0..3),
            )
        }
    }
}
