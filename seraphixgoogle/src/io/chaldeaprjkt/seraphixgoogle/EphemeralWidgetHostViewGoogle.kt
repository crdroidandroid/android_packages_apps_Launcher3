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
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import io.chaldeaprjkt.seraphixgoogle.SeraphixCompanion.allChildren

class EphemeralWidgetHostViewGoogle(context: Context?) : AppWidgetHostView(context) {
    private var listener: DataProviderListener? = null

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
        val weather = Card()

        val leaves = allChildren()
        val textViews = leaves.filterIsInstance<TextView>().filter { it.text?.isNotEmpty() == true }
        val imageViews = leaves.filterIsInstance<ImageView>().filter { it.drawable != null && it.drawable is BitmapDrawable }

        val tempTv = textViews.firstOrNull { tv ->
            val t = tv.text.toString()
            t.contains("°") || t.contains("℃") || t.contains("℉")
        } ?: textViews.lastOrNull()

        weather.text = tempTv?.text?.toString()

        val iconIv = imageViews.firstOrNull { iv ->
            val n = try { resources.getResourceEntryName(iv.id) } catch (_: Exception) { "" }
            n.contains("weather", true) || n.contains("icon", true) || n.contains("temp", true)
        } ?: imageViews.firstOrNull()

        weather.image = (iconIv?.drawable as? BitmapDrawable)?.bitmap

        listener?.onDataUpdated(weather)
    }

    fun setOnUpdateAppWidget(listener: DataProviderListener? = null): EphemeralWidgetHostViewGoogle {
        this.listener = listener
        return this
    }
}
