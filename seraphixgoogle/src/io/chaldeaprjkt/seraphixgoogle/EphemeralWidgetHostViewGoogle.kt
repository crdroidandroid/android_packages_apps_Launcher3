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
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import io.chaldeaprjkt.seraphixgoogle.SeraphixCompanion.allChildren
import io.chaldeaprjkt.seraphixgoogle.SeraphixCompanion.bitmap
import io.chaldeaprjkt.seraphixgoogle.SeraphixCompanion.string
import io.chaldeaprjkt.seraphixgoogle.SeraphixCompanion.takeByName

class EphemeralWidgetHostViewGoogle(context: Context?) : AppWidgetHostView(context) {
    private var listener: DataProviderListener? = null

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
        val weather = Card()
        allChildren().forEach {
            when (it) {
                is ImageView -> it.takeByName<ImageView>(VID_WEATHER_ICON)
                    ?.let { x -> weather.image = x.bitmap }
                is TextView -> it.takeByName<TextView>(VID_WEATHER_TEXT)
                    ?.let { x -> weather.text = x.string }
            }
        }
        listener?.onDataUpdated(weather)
    }

    fun setOnUpdateAppWidget(listener: DataProviderListener? = null): EphemeralWidgetHostViewGoogle {
        this.listener = listener
        return this
    }

    companion object {
        const val VID_WEATHER_TEXT = "title_weather_text"
        const val VID_WEATHER_ICON = "title_weather_icon"
    }
}
