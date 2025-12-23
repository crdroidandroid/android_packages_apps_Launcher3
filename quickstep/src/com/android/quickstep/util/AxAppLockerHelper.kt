/*
 * Copyright (C) 2025 AxionOS Project
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

import android.app.AxSandboxManager.AppLockState
import android.content.Context
import android.os.IBinder
import android.os.RemoteException
import android.os.ServiceManager
import android.util.Log
import com.android.internal.app.IAppLockStateListener
import com.android.internal.app.IAxSandboxManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class AxAppLockerHelper private constructor() {

    companion object {
        private const val TAG = "AxAppLockerHelper"
        
        @Volatile
        private var instance: AxAppLockerHelper? = null

        @JvmStatic
        fun get(): AxAppLockerHelper {
            return instance ?: synchronized(this) {
                instance ?: AxAppLockerHelper().also { instance = it }
            }
        }
    }

    private val sandboxManagerRef = AtomicReference<IAxSandboxManager?>()
    private val appLockCache = ConcurrentHashMap<String, Boolean>()
    private var listenerRegistered = false

    private val listener = object : IAppLockStateListener.Stub() {
        override fun onAppLockStateChanged(packageName: String, locked: Boolean) {
            if (packageName.isBlank()) return
            appLockCache[packageName] = locked
        }
    }

    private val deathRecipient = IBinder.DeathRecipient {
        sandboxManagerRef.set(null)
        listenerRegistered = false
        appLockCache.clear()
    }

    private fun getSandboxManager(): IAxSandboxManager? {
        var manager = sandboxManagerRef.get()
        if (manager == null) {
            val binder = ServiceManager.getService(Context.AX_SANDBOX_SERVICE) ?: return null
            try {
                binder.linkToDeath(deathRecipient, 0)
                manager = IAxSandboxManager.Stub.asInterface(binder)
                sandboxManagerRef.set(manager)
                registerListener(manager)
            } catch (e: RemoteException) {
                Log.w(TAG, "Failed to link to death or register listener", e)
            }
        }
        return manager
    }

    private fun registerListener(manager: IAxSandboxManager) {
        if (!listenerRegistered) {
            try {
                manager.registerAppLockStateListener(listener)
                listenerRegistered = true
            } catch (e: RemoteException) {
                Log.w(TAG, "Failed to register listener", e)
            }
        }
    }

    fun isAppLocked(packageName: String): Boolean {
        if (packageName.isBlank()) return false

        val cached = appLockCache[packageName]
        if (cached != null) return cached

        val manager = getSandboxManager() ?: return false
        return try {
            val locked = AppLockState.fromOrdinal(manager.getAppLockState(packageName))
                .hasAppLock()
            appLockCache[packageName] = locked
            locked
        } catch (e: RemoteException) {
            Log.w(TAG, "RemoteException in isAppLocked: ${e.message}")
            false
        }
    }

    fun isAppLockedWithoutCache(packageName: String): Boolean {
        return try {
            val ordinal = getSandboxManager()?.getAppLockState(packageName) ?: return false
            AppLockState.fromOrdinal(ordinal).hasAppLock()
        } catch (e: RemoteException) {
            Log.w(TAG, "RemoteException in isAppLockedWithoutCache: ${e.message}")
            false
        }
    }
}
