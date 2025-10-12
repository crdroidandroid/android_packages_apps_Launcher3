/*
 * Copyright (C) 2025 The Android Open Source Project
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

import androidx.annotation.UiThread

import com.android.launcher3.Flags.enableOverviewBackgroundWallpaperBlur
import com.android.quickstep.RemoteTargetGluer.RemoteTargetHandle

/** Applies blur either behind launcher surface or live tile app. */
class BlurUtils(private val recentsView: RecentsView<*, *>) {

    private var lastLeashSet: Any? = null

    @UiThread
    fun setDrawLiveTileBelowRecents(drawBelowRecents: Boolean) {
        val liveTileRemoteTargetHandles =
            if (
                recentsView.remoteTargetHandles != null &&
                    recentsView.recentsAnimationController != null
            )
                recentsView.remoteTargetHandles
            else null
        setDrawBelowRecents(drawBelowRecents, liveTileRemoteTargetHandles)
    }

    /**
     * Set surface in [remoteTargetHandles] to be above or below Recents layer, and update the base
     * layer to apply blur to in BaseDepthController.
     */
    @UiThread
    fun setDrawBelowRecents(
        drawBelowRecents: Boolean,
        remoteTargetHandles: Array<RemoteTargetHandle>? = null,
    ) {
        if (!remoteTargetHandles.isNullOrEmpty()) {
            for (h in remoteTargetHandles) {
                h.taskViewSimulator.setDrawsBelowRecents(drawBelowRecents)
            }
        }

        if (!enableOverviewBackgroundWallpaperBlur()) {
            recentsView.depthController?.setBaseSurfaceOverride(null)
            lastLeashSet = null
            return
        }

        // Always clear when not drawing below Recents or when handles are absent.
        val leashOrNull =
            if (drawBelowRecents && !remoteTargetHandles.isNullOrEmpty()) {
                // Pick a valid app leash if present; otherwise null
                remoteTargetHandles
                    .mapNotNull { it.transformParams.targetSet.firstAppTarget?.leash }
                    .filter { it.isValid }
                    .maxByOrNull { it.layerId }
            } else {
                null
            }

        if (leashOrNull !== lastLeashSet) {
            recentsView.depthController?.setBaseSurfaceOverride(leashOrNull)
            lastLeashSet = leashOrNull
        }
    }
}
