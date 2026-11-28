/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.quickstep.util

import android.graphics.Rect
import android.graphics.RectF

/** Where SystemUI's Dynamic Bar wants apps with waiting events to close into. */
object IslandCloseTarget {

    private class Target(val packages: Set<String>, val bounds: RectF, val rotation: Int)

    @Volatile private var target: Target? = null

    @JvmStatic
    fun update(packages: Array<String>?, bounds: Rect?, rotation: Int) {
        target =
            if (packages.isNullOrEmpty() || bounds == null || bounds.isEmpty) null
            else Target(packages.toSet(), RectF(bounds), rotation)
    }

    @JvmStatic
    fun clear() {
        target = null
    }

    /** The island bounds for [packageName] in [rotation], or null to close as usual. */
    @JvmStatic
    fun boundsFor(packageName: String?, rotation: Int): RectF? {
        val current = target ?: return null
        if (packageName == null || packageName !in current.packages) return null
        if (current.rotation != rotation) return null
        return RectF(current.bounds)
    }
}
