/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.launcher3.homescreenfiles

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.widget.Toast
import androidx.compose.ui.text.input.TextFieldValue
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.CustomAppNameStore
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.DialogViewModel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Model for showing a home screen files rename dialog.
 *
 * Also used for renaming apps, in which case the name length is capped and a reset
 * button is shown when the app currently has a custom name.
 */
class HomeScreenFilesRenameDialogViewModel
private constructor(
    activityContext: ActivityContext,
    title: String,
    initialName: String,
    val selectOnFocus: Boolean,
    val maxLength: Int?,
    val resetAction: (() -> Unit)?,
    onSubmit: (String) -> Boolean,
) :
    DialogViewModel<HomeScreenFilesRenameDialogViewModel>(
        title = title,
        content = { viewModel -> HomeScreenFilesRenameDialogView(viewModel) },
        neutralButton =
            activityContext
                .asContext()
                .getString(R.string.home_screen_files_rename_dialog_neutral_button),
        positiveButton =
            activityContext
                .asContext()
                .getString(R.string.home_screen_files_rename_dialog_positive_button),
        onPositiveButtonClick = { viewModel -> onSubmit(viewModel.name.value.text.trim()) },
    ) {

    constructor(
        activityContext: ActivityContext,
        file: HomeScreenFile,
        provider: HomeScreenFilesProvider,
    ) : this(
        activityContext = activityContext,
        title =
            activityContext
                .asContext()
                .getString(
                    if (file.isDirectory) R.string.home_screen_files_rename_dialog_folder_title
                    else R.string.home_screen_files_rename_dialog_file_title
                ),
        initialName = file.displayName,
        selectOnFocus = true,
        maxLength = null,
        resetAction = null,
        onSubmit = { name -> renameFile(activityContext, file, provider, name) },
    )

    // The name which the item should be renamed to. Updated via user interaction with the dialog.
    val name = MutableStateFlow(TextFieldValue(text = initialName))

    companion object {
        const val MAX_APP_NAME_LENGTH = 32

        /** Model for renaming the app represented by [itemInfo]. */
        @JvmStatic
        fun forApp(
            activityContext: ActivityContext,
            itemInfo: ItemInfo,
        ): HomeScreenFilesRenameDialogViewModel {
            val context = activityContext.asContext()
            val appContext = context.applicationContext
            val component = requireNotNull(itemInfo.targetComponent)
            val user = itemInfo.user
            val currentName = itemInfo.title?.toString().orEmpty()

            fun applyName(newName: String?) {
                MODEL_EXECUTOR.execute {
                    val systemName = resolveSystemName(appContext, component, user)
                    val stored = newName?.takeIf { it != systemName }
                    CustomAppNameStore.saveCustomName(appContext, itemInfo, stored)
                    LauncherAppState.getInstance(appContext)
                        .model
                        .onCustomAppNameChanged(component, user)
                }
            }

            return HomeScreenFilesRenameDialogViewModel(
                activityContext = activityContext,
                title = context.getString(R.string.rename_app_label),
                initialName = currentName,
                selectOnFocus = false,
                maxLength = MAX_APP_NAME_LENGTH,
                resetAction =
                    if (CustomAppNameStore.hasCustomName(context, itemInfo)) {
                        { applyName(null) }
                    } else null,
                onSubmit = onSubmit@{ name ->
                    if (name.isEmpty() || name.length > MAX_APP_NAME_LENGTH) {
                        return@onSubmit false
                    }
                    if (name != currentName) {
                        applyName(name)
                    }
                    true
                },
            )
        }

        private fun renameFile(
            activityContext: ActivityContext,
            file: HomeScreenFile,
            provider: HomeScreenFilesProvider,
            name: String,
        ): Boolean {
            // TODO(b/489772913): Implement additional user input validation. Note that
            //  [name] is also sanitized by the media provider downstream so this is
            //  just a UX optimization.
            if (name.isEmpty()) {
                return false
            }

            provider.rename(file.uri, name).whenComplete { result, throwable ->
                if (throwable != null || !result) {
                    activityContext.uiExecutor.post {
                        Toast.makeText(
                                activityContext.asContext(),
                                R.string.something_went_wrong,
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                    }
                }
            }
            return true
        }

        /** Returns the label the system reports for [component], or null if unresolvable. */
        private fun resolveSystemName(
            context: Context,
            component: ComponentName,
            user: UserHandle,
        ): String? {
            val intent =
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(component)
            return context
                .getSystemService(LauncherApps::class.java)
                ?.resolveActivity(intent, user)
                ?.label
                ?.let { Utilities.trim(it) }
        }
    }
}
