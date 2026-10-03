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
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import io.chaldeaprjkt.seraphixgoogle.SeraphixCompanion.isPackageEnabled

class SeraphixDataProvider(
    private val context: Context,
    private val hostId: Int,
    hostWidgetId: Int = -1
) {
    private val widgetManager by lazy { AppWidgetManager.getInstance(context) }
    private val widgetHost by lazy { EphemeralWidgetHostGoogle(context, hostId) }
    private lateinit var widgetHostView: EphemeralWidgetHostViewGoogle
    private val smartspaceProviderComponent = ComponentName(QSB_PACKAGE, SMARTSPACE_PROVIDER)
    private var widgetId = hostWidgetId
    private var isWidgetBound = false
    private var isListening = false

    private fun findProviderInfo(): AppWidgetProviderInfo? =
        widgetManager.installedProviders.firstOrNull { it.provider == smartspaceProviderComponent }

    fun setOnDataUpdated(listener: DataProviderListener? = null): SeraphixDataProvider {
        widgetHost.setOnDataUpdated(listener)
        return this
    }

    fun pauseListening() {
        if (isListening) {
            widgetHost.stopListening()
            isListening = false
        }
    }

    fun resumeListening() {
        if (isWidgetBound && !isListening) {
            widgetHost.startListening()
            isListening = true
        }
    }

    fun bind(onBounded: DataProviderBinder? = null): Boolean {
        if (isWidgetBound) {
            if (widgetId > -1 && widgetManager.getAppWidgetInfo(widgetId) != null) return true
            pauseListening()
            isWidgetBound = false
        }
        if (!context.isPackageEnabled(QSB_PACKAGE)) return false
        val providerInfo = findProviderInfo() ?: return false

        val existing = if (widgetId > -1) widgetManager.getAppWidgetInfo(widgetId) else null
        isWidgetBound = existing != null && existing.provider == providerInfo.provider
        if (!isWidgetBound) {
            if (widgetId > -1) widgetHost.deleteHost()
            widgetId = widgetHost.allocateAppWidgetId()
            isWidgetBound = widgetManager.bindAppWidgetIdIfAllowed(widgetId, smartspaceProviderComponent)
            if (!isWidgetBound) {
                // Don't leak the allocated id when binding isn't permitted.
                widgetHost.deleteAppWidgetId(widgetId)
                widgetId = -1
                return false
            }
        }

        onBounded?.onBound(widgetId)
        resumeListening()
        widgetHostView = widgetHost.createView(context, widgetId, providerInfo)
                as EphemeralWidgetHostViewGoogle
        return true
    }

    fun unbind() {
        if (!isWidgetBound) return
        pauseListening()
        widgetHost.deleteHost()
        if (::widgetHostView.isInitialized) {
            widgetHostView.setOnUpdateAppWidget(null)
        }
        isWidgetBound = false
    }

    companion object {
        const val TAG = "SeraphixDataProvider"
        const val SMARTSPACE_PROVIDER =
            "com.google.android.apps.gsa.staticplugins.smartspace.widget.SmartspaceWidgetProvider"
        const val QSB_PACKAGE = "com.google.android.googlequicksearchbox"
    }
}
