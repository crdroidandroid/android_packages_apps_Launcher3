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
import java.lang.ref.WeakReference

class EphemeralWidgetHostViewGoogle(context: Context?) : AppWidgetHostView(context) {
    private var listener: DataProviderListener? = null
    private var weatherClickTarget: WeakReference<View>? = null

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
        weatherClickTarget = null
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

        val clickTarget = clickableAncestor(tempView) ?: iconView?.let { clickableAncestor(it) }
        weatherClickTarget = clickTarget?.let { WeakReference(it) }

        val chipImages = (clickTarget as? ViewGroup)?.let { chip ->
            leaves.filterIsInstance<ImageView>()
                .filter { it.isDescendantOf(chip) }
                .map { it.contentDescription }
        } ?: emptyList()
        val condition = (sequenceOf(iconView?.contentDescription) + chipImages.asSequence() +
                sequenceOf(
                    TEMP_REGEX.replace(tempView.text ?: "", ""),
                    tempView.contentDescription,
                    clickTarget?.contentDescription
                )).firstNotNullOfOrNull { extractCondition(it) }

        return Card(temp, (iconView?.drawable as? BitmapDrawable)?.bitmap, condition)
    }

    private fun extractCondition(source: CharSequence?): String? {
        if (source.isNullOrBlank()) return null
        return TEMP_REGEX.replace(source, "")
            .split(SEPARATOR_REGEX)
            .map { it.trim() }
            .firstOrNull { part ->
                part.isNotEmpty() && part.none { it.isDigit() } &&
                        GENERIC_WORDS.none { part.equals(it, ignoreCase = true) }
            }
    }

    fun performWeatherClick(): Boolean {
        val target = weatherClickTarget?.get() ?: return false
        return target.hasOnClickListeners() && target.performClick()
    }

    private fun clickableAncestor(view: View): View? {
        var v: View? = view
        while (v != null) {
            if (v.hasOnClickListeners()) return v
            if (v === this) break
            v = v.parent as? View
        }
        return null
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
        private val SEPARATOR_REGEX = Regex("""[,،、;|•·\n]""")
        private val GENERIC_WORDS = listOf("weather", "weather icon", "icon")
    }
}
