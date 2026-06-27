/*
 * Copyright 2025-2026 AxionOS
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
package com.android.quickstep.util

import android.app.AxSandboxManager
import android.content.Context
import android.database.ContentObserver
import android.provider.Settings
import android.util.Log
import com.android.internal.app.IAppLockStateListener
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonTracker
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import javax.inject.Inject

@LauncherAppSingleton
class AxSandboxState
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @Ui private val uiExecutor: Executor,
    lifecycle: DaggerSingletonTracker,
) {
    private val sandboxManager = context.getSystemService(AxSandboxManager::class.java)
    private val lockCache = ConcurrentHashMap<String, Boolean>()
    private val listeners = CopyOnWriteArrayList<Runnable>()

    private val lockStateListener = object : IAppLockStateListener.Stub() {
        override fun onAppLockStateChanged(packageName: String, locked: Boolean) {
            if (packageName.isBlank()) return
            lockCache[packageName] = locked
            dispatchChanged()
        }
    }

    private val configObserver = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            lockCache.clear()
            dispatchChanged()
        }
    }

    init {
        registerLockListener()
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(AxSandboxManager.SETTING_SANDBOX_CONFIG),
            false,
            configObserver,
        )
        lifecycle.addCloseable {
            unregisterLockListener()
            context.contentResolver.unregisterContentObserver(configObserver)
        }
    }

    fun addChangeListener(listener: Runnable) {
        listeners.addIfAbsent(listener)
    }

    fun removeChangeListener(listener: Runnable) {
        listeners.remove(listener)
    }

    fun hasAppLock(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        lockCache[packageName]?.let { return it }
        val hasLock = getHasAppLock(packageName)
        lockCache[packageName] = hasLock
        return hasLock
    }

    private fun registerLockListener() {
        val manager = sandboxManager ?: return
        try {
            manager.registerAppLockStateListener(lockStateListener)
        } catch (e: RuntimeException) {
            Log.w(TAG, "registerLockListener failed", e)
        }
    }

    private fun unregisterLockListener() {
        val manager = sandboxManager ?: return
        try {
            manager.unregisterAppLockStateListener(lockStateListener)
        } catch (e: RuntimeException) {
            Log.w(TAG, "unregisterLockListener failed", e)
        }
    }

    private fun getHasAppLock(packageName: String): Boolean {
        val manager = sandboxManager ?: return false
        return try {
            manager.getAppLockState(packageName).hasAppLock()
        } catch (e: RuntimeException) {
            Log.w(TAG, "getHasAppLock failed", e)
            false
        }
    }

    private fun dispatchChanged() {
        uiExecutor.execute { listeners.forEach { it.run() } }
    }

    private companion object {
        const val TAG = "AxSandboxState"
    }
}
