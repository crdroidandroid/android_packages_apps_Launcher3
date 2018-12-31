/*
 * Copyright (C) 2018 CypherOS
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
package com.android.launcher3.qsb;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.ActionMode.Callback;
import android.view.Menu;
import android.view.MenuItem;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;

public class ActionModeController implements Callback {

    public BaseQsbView mBaseQsbView;
    public String mClipboardText;
    public Intent mSettingsIntent;

    public ActionModeController(BaseQsbView baseQsbView, String clipboardText, Intent intent) {
        mBaseQsbView = baseQsbView;
        mClipboardText = clipboardText;
        mSettingsIntent = intent;
    }

    @SuppressLint({"ResourceType"})
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        actionMode.setTitle(null);
        actionMode.setSubtitle(null);
        actionMode.setTitleOptionalHint(true);
        actionMode.setTag(BaseDraggingActivity.AUTO_CANCEL_ACTION_MODE);
        if (mClipboardText != null) {
            menu.add(0, 16908322, 0, 17039371).setShowAsAction(1);
        }
        if (mSettingsIntent != null) {
            menu.add(0, R.id.hotseat_qsb_menu_item, 0, R.string.hotseat_qsb_preferences).setShowAsAction(8);
        }
        if (mClipboardText != null) {
            return true;
        }
        if (mSettingsIntent != null) {
            return true;
        }
        return false;
    }

    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        return true;
    }

    public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
        if (menuItem.getItemId() != 16908322 || TextUtils.isEmpty(mClipboardText)) {
            if (menuItem.getItemId() == R.id.hotseat_qsb_menu_item) {
                if (mSettingsIntent != null) {
                    mBaseQsbView.getContext().sendBroadcast(mSettingsIntent);
                    actionMode.finish();
                    return true;
                }
            }
            return false;
        }
        actionMode.finish();
        return true;
    }

    public void onDestroyActionMode(ActionMode actionMode) {
    }
}
