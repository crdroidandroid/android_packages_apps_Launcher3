/*
 * Copyright (C) 2022 Project Kaleidoscope
 *               2024-2026 crDroid Android Project
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

package com.android.quickstep.views

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Debug
import android.os.Handler
import android.text.format.Formatter
import android.util.AttributeSet
import android.util.FloatProperty
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView

import com.android.internal.util.MemInfoReader

import com.android.launcher3.DeviceProfile
import com.android.launcher3.Insettable
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.display.DisplayController
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.MultiValueAlpha
import com.android.launcher3.util.NavigationMode

import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.Locale

class MemInfoView(context: Context, attrs: AttributeSet?) :
    TextView(context, attrs), Insettable {

    private val insetsRect = Rect()

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val memInfo = ActivityManager.MemoryInfo()
    private val memInfoReader = MemInfoReader()

    private val multiValueAlpha =
        MultiValueAlpha(this, 2).apply { setUpdateVisibility(true) }

    private val totalResult: String = formatTotalMemory()
    private val memInfoText: String = context.resources.getString(R.string.meminfo_text)

    private var dp: DeviceProfile? = null

    @Volatile
    private var monitorHandler: Handler? = null

    private val worker = MemoryWorker(this)

    init {
        setListener(context)
    }

    override fun setVisibility(visibility: Int) {
        var vis = visibility
        if (vis == VISIBLE && !LauncherPrefs.RECENTS_MEMINFO.get(context)) {
            vis = GONE
        }

        super.setVisibility(vis)

        if (vis == VISIBLE) {
            startMemoryMonitoring()
        } else {
            stopMemoryMonitoring()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateVerticalMargin(DisplayController.getNavigationMode(context))
    }

    override fun setInsets(insets: Rect) {
        insetsRect.set(insets)
        updateVerticalMargin(DisplayController.getNavigationMode(context))
        updatePadding()
    }

    private fun updatePadding() {
        setPadding(insetsRect.left, 0, insetsRect.right, 0)
    }

    fun setDp(dp: DeviceProfile) {
        this.dp = dp
    }

    fun setAlpha(alphaType: Int, alpha: Float) {
        multiValueAlpha.get(alphaType).value = alpha
    }

    fun getAlpha(alphaType: Int): Float = multiValueAlpha.get(alphaType).value

    fun updateVerticalMargin(mode: NavigationMode) {
        val dp = dp ?: return
        val lp = layoutParams as? FrameLayout.LayoutParams ?: return
        lp.setMargins(
            lp.leftMargin,
            lp.topMargin,
            lp.rightMargin,
            dp.overviewActionsClaimedSpaceBelow,
        )
        lp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
    }

    private fun formatTotalMemory(): String {
        activityManager.getMemoryInfo(memInfo)
        val totalMemoryGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val roundedMemoryGB = roundToNearestKnownRamSize(totalMemoryGB)
        return "$roundedMemoryGB GB"
    }

    private fun roundToNearestKnownRamSize(memoryGB: Double): Int {
        val knownSizes = intArrayOf(1, 2, 3, 4, 6, 8, 10, 12, 16, 32, 48, 64)
        if (memoryGB <= 0) return 1
        for (size in knownSizes) {
            if (memoryGB <= size) return size
        }
        return knownSizes.last()
    }

    private fun getZramSize(): Long {
        if (!LauncherPrefs.RECENTS_MEMINFO_ZRAM.get(context)) return 0

        var zramSize = 0L

        try {
            BufferedReader(FileReader("/sys/block/zram0/disksize")).use { reader ->
                reader.readLine()?.trim()?.let { zramSize = it.toLong() }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Primary ZRAM location failed, trying fallback", e)
        } catch (e: NumberFormatException) {
            Log.w(TAG, "Primary ZRAM location failed, trying fallback", e)
        }

        if (zramSize == 0L) {
            try {
                BufferedReader(FileReader("/proc/swaps")).use { reader ->
                    reader.forEachLine { rawLine ->
                        if (rawLine.contains("zram0")) {
                            val parts = rawLine.trim().split(Regex("\\s+"))
                            if (parts.size > 2) {
                                zramSize = parts[2].toLong() * 1024 // KB to bytes
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Fallback ZRAM location not available", e)
            } catch (e: NumberFormatException) {
                Log.w(TAG, "Fallback ZRAM location not available", e)
            }
        }

        return zramSize
    }

    fun setListener(context: Context) {
        setOnClickListener {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                setClassName(
                    "com.android.settings",
                    "com.android.settings.Settings\$DevRunningServicesActivity",
                )
            }
            context.startActivity(intent)
        }
    }

    private fun getTotalBackgroundMemory(): Long {
        var totalBackgroundMemory = 0L
        val runningProcesses = activityManager.runningAppProcesses ?: return 0L

        val pids = IntArray(runningProcesses.size) { runningProcesses[it].pid }
        val memoryInfos = activityManager.getProcessMemoryInfo(pids) ?: return 0L

        val count = minOf(memoryInfos.size, runningProcesses.size)
        for (i in 0 until count) {
            val info = runningProcesses[i]
            if (info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND) {
                totalBackgroundMemory += memoryInfos[i].totalPss.toLong() * 1024L
            }
        }
        return totalBackgroundMemory
    }

    private fun startMemoryMonitoring() {
        if (monitorHandler == null) {
            monitorHandler = MODEL_EXECUTOR.handler
            monitorHandler?.post(worker)
        }
    }

    private fun stopMemoryMonitoring() {
        monitorHandler?.removeCallbacks(worker)
        monitorHandler = null
    }

    private class MemoryWorker(view: MemInfoView) : Runnable {
        private val viewRef = WeakReference(view)

        override fun run() {
            val view = viewRef.get() ?: return
            // Bail out if monitoring was stopped while this callback was queued.
            if (view.monitorHandler == null) return

            view.memInfoReader.readMemInfo()
            val freeMemory = view.memInfoReader.freeSize +
                view.memInfoReader.cachedSize +
                view.getTotalBackgroundMemory()
            val zramSize = view.getZramSize()

            val availResult = Formatter.formatShortFileSize(view.context, freeMemory)
            val text = if (zramSize > 0) {
                val zramResult = Formatter.formatShortFileSize(view.context, zramSize)
                String.format(
                    Locale.getDefault(),
                    view.memInfoText,
                    availResult,
                    "${view.totalResult} + $zramResult",
                )
            } else {
                String.format(Locale.getDefault(), view.memInfoText, availResult, view.totalResult)
            }

            MAIN_EXECUTOR.handler.post { view.text = text }

            // Reschedule only if still monitoring.
            view.monitorHandler?.let {
                it.removeCallbacks(this)
                it.postDelayed(this, REFRESH_INTERVAL_MS)
            }
        }
    }

    override fun onDetachedFromWindow() {
        stopMemoryMonitoring()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val TAG = "MemInfoView"
        private const val REFRESH_INTERVAL_MS = 3000L

        private const val ALPHA_STATE_CTRL = 0
        const val ALPHA_FS_PROGRESS = 1

        @JvmField
        val STATE_CTRL_ALPHA: FloatProperty<MemInfoView> =
            object : FloatProperty<MemInfoView>("state control alpha") {
                override fun get(view: MemInfoView): Float = view.getAlpha(ALPHA_STATE_CTRL)

                override fun setValue(view: MemInfoView, value: Float) {
                    view.setAlpha(ALPHA_STATE_CTRL, value)
                }
            }
    }
}
