/*
 * Copyright (C) 2021 Chaldeaprjkt
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
package io.chaldeaprjkt.seraphixgoogle

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import io.chaldeaprjkt.seraphixgoogle.SeraphixCompanion.visibleLeaves

class EphemeralWidgetHostViewGoogle(context: Context?) : AppWidgetHostView(context) {
    private var listener: DataProviderListener? = null

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
        val card = try {
            if (remoteViews == null) Card() else parseWeather()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse smartspace widget", e)
            Card()
        }
        listener?.onDataUpdated(card)
    }

    private fun parseWeather(): Card {
        val leaves = visibleLeaves()

        var tempView: TextView? = null
        var temp: String? = null
        for (tv in leaves.filterIsInstance<TextView>()) {
            val text = tv.text ?: continue
            val match = TEMP_REGEX.find(text) ?: continue
            tempView = tv
            temp = match.value.replace(WHITESPACE_REGEX, "")
            break
        }
        if (tempView == null || temp.isNullOrEmpty()) return Card()

        val images = leaves.filterIsInstance<ImageView>()
            .filter { it.drawable is BitmapDrawable }

        val iconView = images.firstOrNull { it.idName().contains("weather", true) }
            ?: nearestImage(tempView, images)

        return Card(temp, (iconView?.drawable as? BitmapDrawable)?.bitmap)
    }

    private fun nearestImage(anchor: View, images: List<ImageView>): ImageView? {
        if (images.isEmpty()) return null
        var parent = anchor.parent as? ViewGroup
        var depth = 0
        while (parent != null && depth < MAX_ANCESTOR_DEPTH) {
            val group: ViewGroup = parent
            images.firstOrNull { it.isDescendantOf(group) }?.let { return it }
            if (group === this) break
            parent = group.parent as? ViewGroup
            depth++
        }
        return null
    }

    private fun View.isDescendantOf(ancestor: ViewGroup): Boolean {
        var p = parent
        while (p != null) {
            if (p === ancestor) return true
            p = p.parent
        }
        return false
    }

    private fun View.idName(): String = try {
        if (id == View.NO_ID) "" else resources.getResourceEntryName(id)
    } catch (_: Exception) {
        ""
    }

    fun setOnUpdateAppWidget(listener: DataProviderListener? = null): EphemeralWidgetHostViewGoogle {
        this.listener = listener
        return this
    }

    companion object {
        private const val TAG = "EphemeralWidgetHostView"
        private const val MAX_ANCESTOR_DEPTH = 3

        // Optional sign (ASCII or Unicode minus), 1-3 digits in any script,
        // then a degree sign (optionally followed by C/F) or ℃ / ℉.
        private val TEMP_REGEX = Regex("""[-−]?\p{Nd}{1,3}\s*(?:°\s*[CFcf]?|℃|℉)""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
    }
}
