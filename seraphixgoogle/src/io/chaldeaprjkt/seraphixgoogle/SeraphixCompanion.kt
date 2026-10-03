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

import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup

object SeraphixCompanion {
    fun Context.isPackageEnabled(packageName: String): Boolean {
        return try {
            packageManager.getApplicationInfo(packageName, 0).enabled
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun ViewGroup.allChildren(list: MutableList<View>) {
        for (i in (0 until childCount)) {
            val child = getChildAt(i)
            if (child is ViewGroup) {
                child.allChildren(list)
            } else {
                list.add(child)
            }
        }
    }

    fun ViewGroup.allChildren() = ArrayList<View>().also { allChildren(it) }

    private fun ViewGroup.visibleLeaves(list: MutableList<View>) {
        for (i in (0 until childCount)) {
            val child = getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (child is ViewGroup) {
                child.visibleLeaves(list)
            } else {
                list.add(child)
            }
        }
    }

    fun ViewGroup.visibleLeaves() = ArrayList<View>().also { visibleLeaves(it) }
}
