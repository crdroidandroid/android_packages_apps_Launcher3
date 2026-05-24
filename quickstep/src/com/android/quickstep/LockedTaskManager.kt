package com.android.quickstep

import android.content.Context
import android.os.UserHandle
import android.provider.Settings

class LockedTaskManager private constructor(context: Context) {

    private val contentResolver = context.applicationContext.contentResolver

    fun getLockedPackages(): Set<String> {
        val locked = Settings.System.getStringForUser(
            contentResolver,
            Settings.System.RECENTS_LOCKED_TASKS,
            UserHandle.USER_CURRENT
        ) ?: return emptySet()
        return locked.split(",").filter { it.isNotBlank() }.toSet()
    }

    fun isPackageLocked(packageName: String): Boolean {
        return packageName in getLockedPackages()
    }

    fun setPackageLocked(packageName: String, locked: Boolean) {
        val tasks = getLockedPackages().toMutableSet()
        if (locked) tasks.add(packageName) else tasks.remove(packageName)
        Settings.System.putStringForUser(
            contentResolver,
            Settings.System.RECENTS_LOCKED_TASKS,
            tasks.joinToString(","),
            UserHandle.USER_CURRENT
        )
    }

    companion object {
        @JvmStatic
        private var sInstance: LockedTaskManager? = null

        @JvmStatic
        fun getInstance(context: Context): LockedTaskManager {
            if (sInstance == null) {
                sInstance = LockedTaskManager(context.applicationContext)
            }
            return sInstance!!
        }
    }
}
