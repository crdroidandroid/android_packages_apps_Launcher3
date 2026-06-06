/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.model.tasks

import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.UserHandle
import com.android.launcher3.LauncherModel.ModelUpdateTask
import com.android.launcher3.LauncherSettings
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.util.CustomAppNameStore

/** Rebinds launcher items after a custom app display name is changed. */
class CustomAppNameChangedTask(
    private val component: ComponentName,
    private val user: UserHandle,
) : ModelUpdateTask {

    override fun execute(
        taskController: ModelTaskController,
        dataModel: BgDataModel,
        apps: AllAppsList,
    ) {
        val context = taskController.context
        val iconCache = taskController.iconCache
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val launchIntent =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = this@CustomAppNameChangedTask.component
            }
        val activityInfo = launcherApps.resolveActivity(launchIntent, user)

        synchronized(dataModel) {
            val updatedWorkspaceItems =
                dataModel.updateAndCollectWorkspaceItemInfos(
                    user,
                    { item ->
                        if (
                            item.itemType != LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                                item.targetComponent != component
                        ) {
                            return@updateAndCollectWorkspaceItemInfos false
                        }
                        applyDisplayTitle(context, iconCache, activityInfo, item)
                        true
                    },
                    null,
                )

            apps.updateCustomAppTitle(context, iconCache, component, user)

            taskController.bindUpdatedWorkspaceItems(updatedWorkspaceItems)
            taskController.bindApplicationsIfNeeded()
        }
    }

    companion object {
        private fun applyDisplayTitle(
            context: android.content.Context,
            iconCache: IconCache,
            activityInfo: android.content.pm.LauncherActivityInfo?,
            info: ItemInfoWithIcon,
        ) {
            val customTitle = CustomAppNameStore.getCustomName(context, info)
            if (customTitle != null) {
                info.title = customTitle
                return
            }
            if (activityInfo != null) {
                iconCache.getTitleAndIcon(info, activityInfo, info.matchingLookupFlag)
            }
        }
    }
}
