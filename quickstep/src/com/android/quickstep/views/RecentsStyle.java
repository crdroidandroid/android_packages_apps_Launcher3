/*
 * Copyright (C) 2024-2026 crDroid Android Project
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
package com.android.quickstep.views;

/**
 * Computes the visual transform (scale / translation / elevation / dim) of a single task card
 * for the custom recents layout styles (Kami, iOS, OxygenOS).
 */
final class RecentsStyle {
    static final int STYLE_DEFAULT = 0;
    static final int STYLE_SCALE = 1;
    static final int STYLE_KAMI = 2;
    static final int STYLE_IOS = 3;
    static final int STYLE_OXYGEN = 4;

    /** Maps the {@code pref_recents_style} value to a style id, unknown values are default. */
    static int fromPref(String value) {
        if (value == null) {
            return STYLE_DEFAULT;
        }
        switch (value) {
            case "stock":
                return STYLE_SCALE;
            case "staple":
                return STYLE_KAMI;
            case "ios":
                return STYLE_IOS;
            case "oxygen":
                return STYLE_OXYGEN;
            default:
                return STYLE_DEFAULT;
        }
    }

    /** Whether the style stacks cards on top of each other (as opposed to only scaling them). */
    static boolean isStackStyle(int style) {
        return style == STYLE_KAMI || style == STYLE_IOS || style == STYLE_OXYGEN;
    }

    float scale = 1f;
    float translation;
    float elevation;
    float dim;

    void update(int style, float distance, float taskSize, float pageSpacing,
            float progress, boolean recentsRtl) {
        scale = 1f;
        translation = 0f;
        elevation = 0f;
        dim = 0f;
        if (taskSize <= 0f || progress <= 0f || style == STYLE_DEFAULT) {
            return;
        }
        progress = Math.min(1f, progress);
        float dist = recentsRtl ? distance : -distance;
        float absDist = Math.abs(dist);
        float scaleProgress = Math.min(1f, absDist / Math.max(1f, taskSize + pageSpacing));
        if (style == STYLE_SCALE) {
            scale = 1f - 0.15f * scaleProgress * progress;
            return;
        }
        float minScale = style == STYLE_OXYGEN ? 0.92f : 0.85f;
        float targetScale = 1f - (1f - minScale) * scaleProgress;

        if (style == STYLE_KAMI) {
            float excess = Math.max(0f, absDist - taskSize * 0.60f);
            float squish = Math.min(excess, (float) (Math.log10(1f + excess) * 12f));
            translation = -Math.signum(dist) * (excess - squish);
            elevation = -(excess / taskSize) * 20f;
            targetScale *= Math.max(0.75f, 1f - excess / (taskSize * 2.5f) * 0.15f);
        } else if (style == STYLE_IOS && dist < 0f) {
            float excess = Math.max(0f, absDist - taskSize * 0.1f);
            float squish = Math.min(excess, (float) (Math.log10(1f + excess) * 45f));
            translation = excess - squish;
            elevation = -(excess / taskSize) * 24f;
            float depth = Math.min(1f, excess / (taskSize * 1.5f));
            dim = 0.3f * depth;
            targetScale *= 1f - depth * 0.15f;
        } else if (style == STYLE_OXYGEN && dist < 0f) {
            float bend = taskSize * 0.02f;
            float visualDistance = 2f * absDist
                    / ((float) Math.sqrt(1f + absDist / bend) + 1f);
            translation = absDist - visualDistance;
            elevation = -(absDist / taskSize) * 12f;
            targetScale *= Math.max(0.85f, 1f - absDist / (taskSize * 3f) * 0.1f);
        }

        scale = 1f + (targetScale - 1f) * progress;
        translation *= (recentsRtl ? 1f : -1f) * progress;
        elevation *= progress;
        dim *= progress;
    }
}
