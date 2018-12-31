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
package com.android.launcher3.qsb.configs;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Color;

import java.util.ArrayList;

public class QsbConfiguration {

    public static QsbConfiguration INSTANCE;

    public static QsbConfiguration getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new QsbConfiguration(context.getApplicationContext());
        }
        return INSTANCE;
    }

    public QsbConfiguration(Context context) {
    }

    public int getBackgroundColor() {
        return -1711604998;
    }

    public int getMicOpacity() {
        getBackgroundColor();
        return Color.alpha(-1711604998);
    }

    public float micStrokeWidth() {
        return 0.0f;
    }

    public String hintTextValue() {
        return "";
    }

    public boolean useTwoBubbles() {
        return false;
    }

    public boolean hintIsForAssistant() {
        return false;
    }
}
