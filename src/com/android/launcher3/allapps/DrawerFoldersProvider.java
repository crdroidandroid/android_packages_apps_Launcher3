/*
 * Copyright (C) 2026 crDroid Android Project
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
package com.android.launcher3.allapps;

import android.view.ViewGroup;

import androidx.annotation.ColorInt;

import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.util.ComponentKey;

import java.util.List;
import java.util.Set;

/** Supplies the app drawer with its folders. */
public interface DrawerFoldersProvider {

    List<FolderInfo> getDrawerFolders();

    /** Apps inside drawer folders, hidden from the top level of the drawer. */
    Set<ComponentKey> getFolderMemberKeys();

    /** Changes whenever cached folder icons were rebuilt. */
    int getIconGeneration();

    void bindFolderIcon(FolderInfo info, ViewGroup host, boolean horizontal,
            @ColorInt int textColor);

    void onDrawerDropCompleted();

    void onDeviceProfileChanged();
}
