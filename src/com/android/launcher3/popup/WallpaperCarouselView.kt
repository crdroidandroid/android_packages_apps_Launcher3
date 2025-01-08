package com.android.launcher3.popup

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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
        removeAllViews()

        val appliedIndex = wallpapers.indexOfFirst { it.rank == 0 }.let { if (it >= 0) it else 0 }
        currentItemIndex = appliedIndex

        val totalWidth = calculateTotalWidth()
        val firstItemWidth = totalWidth * 0.4
        val itemWidth = calculateItemWidth(totalWidth, wallpapers.size, firstItemWidth)
        val margin = (totalWidth * 0.03).toInt()

        wallpapers.forEachIndexed { index, wallpaper ->
            val cardView = createCardView(index, firstItemWidth, itemWidth, margin, wallpaper)
            addView(cardView)
            loadWallpaperImage(wallpaper, cardView, index == currentItemIndex)
        }
        loadingView.visibility = GONE
    }

    private fun calculateTotalWidth(): Int {
        return width.takeIf { it > 0 }
            ?: (deviceProfile.deviceProperties.widthPx * if (deviceProfile.deviceProperties.isLandscape || deviceProfile.deviceProperties.isPhone) 0.5 else 0.8).toInt()
    }

    private fun calculateItemWidth(totalWidth: Int, itemCount: Int, firstItemWidth: Double): Double {
        if (itemCount <= 1) return totalWidth.toDouble()
        val remainingWidth = totalWidth - firstItemWidth
        val marginBetweenItems = totalWidth * 0.03
        return (remainingWidth - (marginBetweenItems * (itemCount - 1))) / (itemCount - 1)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createCardView(
        index: Int,
        firstItemWidth: Double,
        itemWidth: Double,
        margin: Int,
        wallpaper: Wallpaper,
    ): CardView {
        return CardView(context).apply {
            radius = Themes.getDialogCornerRadius(context) / 2
            layoutParams = LayoutParams(
                if (index == currentItemIndex) firstItemWidth.toInt() else itemWidth.toInt(),
                LayoutParams.MATCH_PARENT,
            ).apply { setMargins(if (index > 0) margin else 0, 0, 0, 0) }

            setOnTouchListener { _, ev ->
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                    animateWidthTransition(index, firstItemWidth, itemWidth)
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
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = BitmapFactory.decodeFile(wallpaper.imagePath) ?: return@runCatching false
                    WallpaperManager.getInstance(context).setBitmap(
                        bmp, null, true, WallpaperManager.FLAG_SYSTEM
                    )
                    WallpaperService.INSTANCE.get(context).updateWallpaperRank(wallpaper)
                    true
                }.getOrDefault(false)
            }

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

    private fun addIconFrameToCenter(cardView: CardView? = getChildAt(currentItemIndex) as CardView) {
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
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(calculateTotalWidth(), MeasureSpec.EXACTLY),
            heightMeasureSpec,
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
        removeAllViews()
    }

    private fun animateWidthTransition(newIndex: Int, firstItemWidth: Double, itemWidth: Double) {
        for (i in 0 until childCount) {
            (getChildAt(i) as? CardView)?.let { cardView ->
                val targetWidth = if (i == newIndex) firstItemWidth.toInt() else itemWidth.toInt()
                if (cardView.layoutParams.width != targetWidth) {
                    ValueAnimator.ofInt(cardView.layoutParams.width, targetWidth).apply {
                        duration = 300L
                        addUpdateListener {
                            cardView.layoutParams.width = it.animatedValue as Int
                            cardView.requestLayout()
                        }
                        start()
                    }
                }
            }
        }
    }
}
