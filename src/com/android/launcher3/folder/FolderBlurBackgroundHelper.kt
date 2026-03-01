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

package com.android.launcher3.folder

import android.graphics.Canvas
import android.view.View
import com.android.launcher3.dagger.ActivityContextSingleton
import com.android.launcher3.graphics.PathWrapper
import javax.inject.Inject

/**
 * Helper class that creates and updates the blur drawable used for folders.
 */
@ActivityContextSingleton
open class FolderBlurBackgroundHelper
@Inject
constructor() {

    open fun prepareToOpen(folder: Folder) {}

    open fun drawBlur(canvas: Canvas, pathWrapper: PathWrapper?, view: View) {}

    open fun folderCloseComplete() {}
}
