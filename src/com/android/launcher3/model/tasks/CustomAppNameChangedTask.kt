/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.model.tasks

import android.content.ComponentName
import android.os.UserHandle
import com.android.launcher3.LauncherModel.ModelUpdateTask
import com.android.launcher3.LauncherSettings
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelTaskController

/**
 * Rebinds launcher items after a custom app display name is changed. The custom name itself is
 * applied by [com.android.launcher3.icons.IconCache] whenever a title is (re)loaded, so this task
 * only needs to reload titles for the affected component and rebind.
 */
class CustomAppNameChangedTask(
    private val component: ComponentName,
    private val user: UserHandle,
) : ModelUpdateTask {

    override fun execute(
        taskController: ModelTaskController,
        dataModel: BgDataModel,
        apps: AllAppsList,
    ) {
        val iconCache = taskController.iconCache

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
                        iconCache.getTitleAndIcon(item, item.matchingLookupFlag)
                        true
                    },
                )

            apps.updateCustomAppTitle(component, user)

            taskController.bindUpdatedWorkspaceItems(updatedWorkspaceItems)
            taskController.bindApplicationsIfNeeded()
        }
    }
}
