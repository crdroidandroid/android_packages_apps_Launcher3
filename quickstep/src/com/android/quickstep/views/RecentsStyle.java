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

    // Length (as a fraction of the task size) over which a stacked card still visibly travels
    // once it starts getting pulled into the stack. Sized to match the old pixel-based log curves
    // (log10(1 + x) * 12 / * 45) on a typical phone, but independent of the display density.
    private static final float KAMI_SQUISH = 0.012f;
    private static final float IOS_SQUISH = 0.065f;

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
        // Eased so the scale starts and stops changing gradually, both at the centre and at the
        // rest position of the neighbouring cards (one page away).
        float scaleProgress = smoothstep(absDist / Math.max(1f, taskSize + pageSpacing));
        if (style == STYLE_SCALE) {
            scale = 1f - 0.15f * scaleProgress * progress;
            return;
        }
        float minScale = style == STYLE_OXYGEN ? 0.92f : 0.85f;
        float targetScale = 1f - (1f - minScale) * scaleProgress;

        if (style == STYLE_KAMI) {
            float excess = Math.max(0f, absDist - taskSize * 0.60f);
            translation = -Math.signum(dist) * stackPull(excess, taskSize * KAMI_SQUISH);
            elevation = -(excess / taskSize) * 20f;
            targetScale *= 1f - 0.15f * smoothstep(excess / (taskSize * 2.5f));
        } else if (style == STYLE_IOS && dist < 0f) {
            float excess = Math.max(0f, absDist - taskSize * 0.1f);
            translation = stackPull(excess, taskSize * IOS_SQUISH);
            elevation = -(excess / taskSize) * 24f;
            float depth = smoothstep(excess / (taskSize * 1.5f));
            dim = 0.3f * depth;
            targetScale *= 1f - depth * 0.15f;
        } else if (style == STYLE_OXYGEN && dist < 0f) {
            float bend = taskSize * 0.02f;
            float visualDistance = 2f * absDist
                    / ((float) Math.sqrt(1f + absDist / bend) + 1f);
            translation = absDist - visualDistance;
            elevation = -(absDist / taskSize) * 12f;
            targetScale *= 1f - 0.1f * smoothstep(absDist / (taskSize * 3f));
        }

        scale = 1f + (targetScale - 1f) * progress;
        translation *= (recentsRtl ? 1f : -1f) * progress;
        elevation *= progress;
        dim *= progress;
    }

    /**
     * How far a card that is {@code excess} past the stack threshold is pulled back into the stack.
     *
     * <p>The card keeps {@code squish * ln(1 + excess / squish)} of its travel, which starts at
     * full speed and slows down smoothly, so the pull starts at zero speed instead of kicking in
     * abruptly the way {@code min(excess, log(...))} did.
     */
    private static float stackPull(float excess, float squish) {
        if (excess <= 0f || squish <= 0f) {
            return 0f;
        }
        return excess - squish * (float) Math.log1p(excess / squish);
    }

    /** Clamps {@code t} to [0, 1] and eases it in and out, with zero slope at both ends. */
    private static float smoothstep(float t) {
        if (t <= 0f) {
            return 0f;
        }
        if (t >= 1f) {
            return 1f;
        }
        return t * t * (3f - 2f * t);
    }
}
