/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.util

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.os.UserHandle
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo

object CustomAppNameStore {

    private const val CUSTOM_NAMES_PREFS = "custom_app_names"

    @JvmStatic
    fun supportsCustomName(info: ItemInfo?): Boolean =
        info != null &&
            (info is AppInfo || info is WorkspaceItemInfo) &&
            info.itemType == ITEM_TYPE_APPLICATION &&
            info.targetComponent != null

    @JvmStatic
    fun customNameKey(info: ItemInfo): String? {
        if (!supportsCustomName(info)) return null
        val cn = info.targetComponent ?: return null
        return keyFor(cn.packageName, cn.className, info.user)
    }

    @JvmStatic
    fun saveCustomName(context: Context, info: ItemInfo, name: String?) {
        val key = customNameKey(info) ?: return
        val editor = prefs(context).edit()
        if (name == null) editor.remove(key) else editor.putString(key, name)
        editor.apply()
    }

    @JvmStatic
    fun getCustomName(context: Context, info: ItemInfo): String? =
        customNameKey(info)?.let { prefs(context).getString(it, null) }

    @JvmStatic
    fun hasCustomName(context: Context, info: ItemInfo): Boolean =
        getCustomName(context, info) != null

    @JvmStatic
    fun removeCustomNamesForPackage(context: Context, packageName: String, user: UserHandle) {
        val prefix = "$packageName/"
        val suffix = "/${user.identifier}"
        val prefs = prefs(context)
        val stale = prefs.all.keys.filter { it.startsWith(prefix) && it.endsWith(suffix) }
        if (stale.isEmpty()) return
        val editor = prefs.edit()
        stale.forEach(editor::remove)
        editor.apply()
    }

    @JvmStatic
    fun registerUninstallCleanup(context: Context): SafeCloseable {
        val appContext = context.applicationContext
        val launcherApps = appContext.getSystemService(LauncherApps::class.java)!!
        val callback =
            object : LauncherApps.Callback() {
                override fun onPackageRemoved(packageName: String, user: UserHandle) {
                    if (launcherApps.getActivityList(packageName, user).isNotEmpty()) return
                    removeCustomNamesForPackage(appContext, packageName, user)
                }

                override fun onPackageAdded(packageName: String, user: UserHandle) {}

                override fun onPackageChanged(packageName: String, user: UserHandle) {}

                override fun onPackagesAvailable(
                    packageNames: Array<out String>,
                    user: UserHandle,
                    replacing: Boolean,
                ) {}

                override fun onPackagesUnavailable(
                    packageNames: Array<out String>,
                    user: UserHandle,
                    replacing: Boolean,
                ) {}
            }
        launcherApps.registerCallback(callback, Executors.MODEL_EXECUTOR.handler)
        return SafeCloseable { launcherApps.unregisterCallback(callback) }
    }

    private fun keyFor(packageName: String, className: String, user: UserHandle) =
        "$packageName/$className/${user.identifier}"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(CUSTOM_NAMES_PREFS, Context.MODE_PRIVATE)
}
