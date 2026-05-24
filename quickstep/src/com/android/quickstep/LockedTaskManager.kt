/*
 * Copyright (C) 2025-2026 The crDroid Android Project
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
package com.android.quickstep

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.shared.recents.model.Task
import java.util.concurrent.CopyOnWriteArrayList

class LockedTaskManager private constructor(context: Context) {

    private val contentResolver = context.applicationContext.contentResolver
    private val listeners = CopyOnWriteArrayList<Runnable>()

    @Volatile private var cachedPackages: Set<String>? = null

    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                cachedPackages = null
                listeners.forEach { it.run() }
            }
        }

    init {
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.RECENTS_LOCKED_TASKS),
            /* notifyForDescendants= */ false,
            observer,
            UserHandle.USER_ALL,
        )
    }

    fun getLockedPackages(): Set<String> = cachedPackages ?: readLockedPackages().also {
        cachedPackages = it
    }

    fun isPackageLocked(packageName: String?): Boolean =
        !packageName.isNullOrBlank() && packageName in getLockedPackages()

    fun setPackageLocked(packageName: String, locked: Boolean) =
        setPackagesLocked(listOf(packageName), locked)

    @Synchronized
    fun setPackagesLocked(packageNames: Collection<String>, locked: Boolean) {
        val packages = readLockedPackages().toMutableSet()
        val changed =
            if (locked) packages.addAll(packageNames) else packages.removeAll(packageNames.toSet())
        if (!changed) return
        cachedPackages = packages
        Settings.System.putStringForUser(
            contentResolver,
            Settings.System.RECENTS_LOCKED_TASKS,
            packages.sorted().joinToString(SEPARATOR),
            UserHandle.USER_CURRENT,
        )
    }

    fun addChangeListener(listener: Runnable) {
        listeners.addIfAbsent(listener)
    }

    fun removeChangeListener(listener: Runnable) {
        listeners.remove(listener)
    }

    private fun readLockedPackages(): Set<String> =
        Settings.System.getStringForUser(
                contentResolver,
                Settings.System.RECENTS_LOCKED_TASKS,
                UserHandle.USER_CURRENT,
            )
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet() ?: emptySet()

    companion object {
        private const val SEPARATOR = ","

        @Volatile private var instance: LockedTaskManager? = null

        @JvmStatic
        fun getInstance(context: Context): LockedTaskManager =
            instance
                ?: synchronized(this) {
                    instance ?: LockedTaskManager(context.applicationContext).also { instance = it }
                }

        @JvmStatic
        fun getPackageName(task: Task?): String? =
            task?.key?.packageName?.takeIf { it.isNotBlank() }
                ?: task?.topComponent?.packageName?.takeIf { it.isNotBlank() }
    }
}
