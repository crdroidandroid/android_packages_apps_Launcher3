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

package com.android.launcher3.shapes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.android.launcher3.R
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.collapsingtoolbar.R as CollapsingToolbarR

/** Settings activity hosting the icon shape picker, or the custom shape editor. */
class IconShapeSettingsActivity : CollapsingToolbarBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        val isEditor = intent.getBooleanExtra(EXTRA_EDITOR, false)
        val target = ShapeTarget.fromName(intent.getStringExtra(EXTRA_TARGET))
        setTitle(if (isEditor) target.customTitle else target.title)

        if (savedInstanceState == null) {
            val fragment =
                if (isEditor) IconShapeEditorFragment() else IconShapeSettingsFragment()
            fragment.arguments = Bundle().apply { putString(EXTRA_TARGET, target.name) }
            supportFragmentManager
                .beginTransaction()
                .replace(CollapsingToolbarR.id.content_frame, fragment)
                .commit()
        }
    }

    override fun onNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val EXTRA_EDITOR = "icon_shape_editor"
        /** [ShapeTarget] name, also passed to the hosted fragment as an argument. */
        const val EXTRA_TARGET = "shape_target"

        /** Returns an intent opening the shape picker for [target]. */
        @JvmStatic
        fun createIntent(context: Context, target: ShapeTarget): Intent =
            Intent(context, IconShapeSettingsActivity::class.java)
                .putExtra(EXTRA_TARGET, target.name)

        /** Returns an intent opening the custom shape editor for [target]. */
        fun createEditorIntent(context: Context, target: ShapeTarget): Intent =
            createIntent(context, target).putExtra(EXTRA_EDITOR, true)
    }
}
