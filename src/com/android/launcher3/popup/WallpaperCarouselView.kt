package com.android.launcher3.popup

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.android.launcher3.DeviceProfile
import com.android.launcher3.R
import com.android.launcher3.data.wallpaper.Wallpaper
import com.android.launcher3.data.wallpaper.service.WallpaperService
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.IconFrame
import java.io.File

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WallpaperCarouselView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val deviceProfile: DeviceProfile by lazy {
        (ActivityContext.lookupContext(context) as ActivityContext).deviceProfile
    }
    private var currentItemIndex = 0
    private val iconFrame = IconFrame(context).apply {
        setIcon(R.drawable.ic_tick)
        setBackgroundWithRadius(Themes.getColorAccent(context), 100F)
    }
    private val loadingView = ProgressBar(context).apply { isIndeterminate = true }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var applyJob: Job? = null
    private var weightAnimator: ValueAnimator? = null

    init {
        orientation = HORIZONTAL
        addView(loadingView)
        observeWallpapers()
    }

    private fun observeWallpapers() {
        loadingView.visibility = VISIBLE
        scope.launch {
            val wallpapers = withContext(Dispatchers.IO) {
                runCatching { WallpaperService.INSTANCE.get(context).getTopWallpapers() }
                    .getOrDefault(emptyList())
            }

            if (!isAttachedToWindow) return@launch

            visibility = if (wallpapers.isEmpty()) GONE else VISIBLE
            if (wallpapers.isNotEmpty()) displayWallpapers(wallpapers) else loadingView.visibility = GONE
        }
    }

    private fun displayWallpapers(wallpapers: List<Wallpaper>) {
        weightAnimator?.cancel()
        removeAllViews()

        val appliedIndex = wallpapers.indexOfFirst { it.rank == 0 }.let { if (it >= 0) it else 0 }
        currentItemIndex = appliedIndex

        val margin = (desiredWidth() * GAP_FRACTION).toInt()

        wallpapers.forEachIndexed { index, wallpaper ->
            val cardView = createCardView(index, margin, wallpaper)
            addView(cardView)
            loadWallpaperImage(wallpaper, cardView, index == currentItemIndex)
        }
        loadingView.visibility = GONE
    }

    private fun desiredWidth(): Int {
        val props = deviceProfile.deviceProperties
        val fraction = if (props.isLandscape || props.isPhone) 0.5 else 0.8
        return (props.widthPx * fraction).toInt()
    }

    private fun weightFor(index: Int, expandedIndex: Int): Float =
        if (index == expandedIndex) EXPANDED_WEIGHT else COLLAPSED_WEIGHT

    @SuppressLint("ClickableViewAccessibility")
    private fun createCardView(
        index: Int,
        margin: Int,
        wallpaper: Wallpaper,
    ): CardView {
        return CardView(context).apply {
            radius = Themes.getDialogCornerRadius(context) / 2
            layoutParams = LayoutParams(
                0,
                LayoutParams.MATCH_PARENT,
                weightFor(index, currentItemIndex),
            ).apply { setMargins(if (index > 0) margin else 0, 0, 0, 0) }

            setOnTouchListener { _, ev ->
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                    animateExpansion(index)
                }
                false
            }
            setOnClickListener {
                currentItemIndex = index
                setWallpaper(wallpaper, this)
            }
        }
    }

    private fun loadWallpaperImage(wallpaper: Wallpaper, cardView: CardView, isCurrent: Boolean) {
        val path = wallpaper.imagePath
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(path)
                    if (!file.exists() || !file.canRead()) return@runCatching null
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeFile(file.path, opts)
                }.getOrNull()
            }
            if (!isAttachedToWindow) return@launch
            if (bitmap != null) addImageView(cardView, bitmap, isCurrent)
        }
    }

    private fun addImageView(cardView: CardView, bitmap: Bitmap, isCurrent: Boolean) {
        val imageView = ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_deepshortcut_placeholder))
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 1f
        }
        cardView.addView(imageView)
        imageView.alpha = 0f
        imageView.setImageBitmap(bitmap)
        imageView.animate().alpha(1f).setDuration(200L).start()
        if (isCurrent) {
            addIconFrameToCenter(cardView)
        }
    }

    private fun setWallpaper(wallpaper: Wallpaper, currentCardView: CardView) {
        val spinner = createLoadingSpinner()

        currentCardView.removeView(iconFrame)
        currentCardView.addView(spinner)

        applyJob?.cancel()
        applyJob = scope.launch {
            val success =
                WallpaperService.INSTANCE.get(context)
                    .applyWallpaper(wallpaper, WallpaperManager.getInstance(context))
/*
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = BitmapFactory.decodeFile(wallpaper.imagePath) ?: return@runCatching false
                    WallpaperManager.getInstance(context).setBitmap(
                        bmp, null, true, WallpaperManager.FLAG_SYSTEM
                    )
                    if (LauncherPrefs.WALLPAPER_CAROUSEL_LOCKSCREEN.get(context)) {
                        WallpaperManager.getInstance(context).setBitmap(
                            bmp, null, true, WallpaperManager.FLAG_LOCK
                        )
                    }
                    WallpaperService.INSTANCE.get(context).updateWallpaperRank(wallpaper)
                    true
                }.getOrDefault(false)
            }
*/
            if (!isAttachedToWindow) return@launch
            currentCardView.removeView(spinner)

            if (success) {
                addIconFrameToCenter(currentCardView)
                observeWallpapers()
            }
        }
    }

    private fun createLoadingSpinner() = ProgressBar(context).apply {
        isIndeterminate = true
        layoutParams = FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun addIconFrameToCenter(cardView: CardView? = getChildAt(currentItemIndex) as? CardView) {
        if (cardView == null) return
        (iconFrame.parent as? ViewGroup)?.removeView(iconFrame)
        cardView.addView(
            iconFrame,
            FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            },
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(desiredWidth(), widthMeasureSpec)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            heightMeasureSpec,
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        weightAnimator?.cancel()
        scope.cancel()
        removeAllViews()
    }

    private fun animateExpansion(newIndex: Int) {
        val cards = (0 until childCount).mapNotNull { getChildAt(it) as? CardView }
        if (cards.isEmpty()) return

        val from = cards.map { (it.layoutParams as LayoutParams).weight }
        val to = cards.indices.map { weightFor(it, newIndex) }
        if (from == to) return

        weightAnimator?.cancel()
        weightAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300L
            addUpdateListener { animator ->
                val f = animator.animatedFraction
                cards.forEachIndexed { i, card ->
                    (card.layoutParams as LayoutParams).weight = from[i] + (to[i] - from[i]) * f
                }
                requestLayout()
            }
            start()
        }
    }

    private companion object {
        const val GAP_FRACTION = 0.03
        const val EXPANDED_WEIGHT = 2.5f
        const val COLLAPSED_WEIGHT = 1f
    }
}
