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

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import io.chaldeaprjkt.seraphixgoogle.SeraphixCompanion.isPackageEnabled

class SeraphixDataProvider(
    private val context: Context,
    private val hostId: Int,
    hostWidgetId: Int = -1
) {
    private val widgetManager by lazy { AppWidgetManager.getInstance(context) }
    private val providerInfo by lazy {
        widgetManager.installedProviders
            .firstOrNull { it.provider == smartspaceProviderComponent }
    }
    private val widgetHost by lazy { EphemeralWidgetHostGoogle(context, hostId) }
    private lateinit var widgetHostView: EphemeralWidgetHostViewGoogle
    private val smartspaceProviderComponent = ComponentName(QSB_PACKAGE, SMARTSPACE_PROVIDER)
    private var widgetId = hostWidgetId
    private var isWidgetBound = false

    fun setOnDataUpdated(listener: DataProviderListener? = null): SeraphixDataProvider {
        widgetHost.setOnDataUpdated(listener)
        return this
    }

    fun bind(onBounded: DataProviderBinder? = null) {
        if (isWidgetBound) return
        if (!context.isPackageEnabled(QSB_PACKAGE)) {
            Log.i(TAG, "No $QSB_PACKAGE installed/enabled")
            return
        }

        val wInfo = widgetManager.getAppWidgetInfo(widgetId)
        isWidgetBound = wInfo != null && providerInfo?.provider == wInfo.provider
        if (!isWidgetBound) {
            if (widgetId > -1) {
                widgetHost.deleteHost()
            }
            widgetId = widgetHost.allocateAppWidgetId()
            isWidgetBound =
                widgetManager.bindAppWidgetIdIfAllowed(widgetId, smartspaceProviderComponent)
        }

        if (isWidgetBound) {
            onBounded?.onBound(widgetId)
            widgetHostView = widgetHost.createView(context, widgetId, providerInfo)
                    as EphemeralWidgetHostViewGoogle
            widgetHost.startListening()
        }
    }

    fun unbind() {
        if (!isWidgetBound) return
        widgetHost.stopListening()
        widgetHost.deleteHost()
        isWidgetBound = false
    }

    companion object {
        const val TAG = "SeraphixDataProvider"
        const val SMARTSPACE_PROVIDER =
            "com.google.android.apps.gsa.staticplugins.smartspace.widget.SmartspaceWidgetProvider"
        const val QSB_PACKAGE = "com.google.android.googlequicksearchbox"
    }
}
