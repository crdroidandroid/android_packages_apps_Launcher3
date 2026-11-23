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

package com.android.launcher3;

import android.graphics.Camera;
import android.graphics.Matrix;

public enum PageTransitionEffect {

    NONE("none") {
        @Override
        void apply(PageTransform t, float p, float w, float h, float d) { }
    },

    SIMPLE("simple") {
        @Override
        void apply(PageTransform t, float p, float w, float h, float d) {
            float scale = 1f - 0.12f * Math.abs(p);
            t.scaleX = scale;
            t.scaleY = scale;
            t.alpha = 1f - 0.35f * Math.abs(p);
        }
    },

    CUBE("cube") {
        @Override
        void apply(PageTransform t, float p, float w, float h, float d) {
            // Pages turn around the edge they share, like the outside of a box.
            float gap = (d - w) / 2f;
            t.pivotX = p > 0 ? w + gap : -gap;
            t.rotationY = -90f * p;
        }
    },

    CARD_STACK("card_stack") {
        @Override
        void apply(PageTransform t, float p, float w, float h, float d) {
            // The leading page stays behind and sinks while the next card slides over it.
            if (p > 0) {
                float scale = 1f - 0.2f * p;
                t.translationX = p * d;
                t.scaleX = scale;
                t.scaleY = scale;
                t.alpha = 1f - p;
            }
        }
    },

    TABLET("tablet") {
        @Override
        void apply(PageTransform t, float p, float w, float h, float d) {
            float scale = 1f - 0.08f * Math.abs(p);
            t.rotationY = 28f * p;
            t.scaleX = scale;
            t.scaleY = scale;
        }
    },

    REVOLVING_DOOR("revolving_door") {
        @Override
        void apply(PageTransform t, float p, float w, float h, float d) {
            // Each page swings around its own vertical center line as it slides.
            t.rotationY = -90f * p;
            t.alpha = 1f - 0.3f * Math.abs(p);
        }
    },

    FLIP("flip") {
        @Override
        void apply(PageTransform t, float p, float w, float h, float d) {
            t.translationX = p * d;
            t.rotationY = -180f * p;
            // Hide the page once its back would face the viewer.
            t.alpha = Math.abs(p) < 0.5f ? 1f : 0f;
        }
    },

    FLIP_VERTICAL("flip_vertical") {
        @Override
        void apply(PageTransform t, float p, float w, float h, float d) {
            t.translationX = p * d;
            t.rotationX = 180f * p;
            t.alpha = Math.abs(p) < 0.5f ? 1f : 0f;
        }
    },

    IN_AND_OUT("in_and_out") {
        @Override
        void apply(PageTransform t, float p, float w, float h, float d) {
            // The leaving page grows towards the viewer, the next one grows in from behind.
            float scale = 1f + 0.5f * p;
            t.translationX = p * d;
            t.scaleX = scale;
            t.scaleY = scale;
            t.alpha = 1f - Math.abs(p);
        }
    },

    ACCORDION("accordion") {
        @Override
        void apply(PageTransform t, float p, float w, float h, float d) {
            // Both pages stay pinned to the screen edges and fold into each other.
            t.translationX = p * d;
            t.pivotX = p > 0 ? 0f : w;
            t.scaleX = 1f - Math.abs(p);
        }
    },

    GLASS("glass") {
        @Override
        void apply(PageTransform t, float p, float w, float h, float d) {
            // Panes glide at half speed and blend through each other.
            float scale = 1f - 0.06f * Math.abs(p);
            t.translationX = 0.5f * p * d;
            t.scaleX = scale;
            t.scaleY = scale;
            t.alpha = 1f - Math.abs(p);
        }
    },

    WIPE("wipe") {
        @Override
        void apply(PageTransform t, float p, float w, float h, float d) {
            // Pages stay in place and a moving edge reveals the next one.
            t.translationX = p * d;
            t.hasClip = true;
            if (p > 0) {
                t.clipLeft = 0f;
                t.clipRight = w * (1f - p);
            } else {
                t.clipLeft = -w * p;
                t.clipRight = w;
            }
        }
    },

    ZOOM_FADE("zoom_fade") {
        @Override
        void apply(PageTransform t, float p, float w, float h, float d) {
            float scale = 1f - 0.3f * Math.abs(p);
            t.translationX = p * d;
            t.scaleX = scale;
            t.scaleY = scale;
            t.alpha = 1f - Math.abs(p);
        }
    },

    THROW("throw") {
        @Override
        void apply(PageTransform t, float p, float w, float h, float d) {
            // Pages are tossed along an arc around a point below the screen.
            t.pivotY = h * 1.5f;
            t.rotation = -18f * p;
            t.translationY = 0.1f * h * Math.abs(p);
            t.alpha = 1f - 0.5f * Math.abs(p);
        }
    };

    private static final float CAMERA_DISTANCE_FACTOR = 1.5f;
    private static final float CAMERA_UNITS_PER_INCH = 72f;

    private final String mValue;

    PageTransitionEffect(String value) {
        mValue = value;
    }

    public String getValue() {
        return mValue;
    }

    abstract void apply(PageTransform t, float p, float w, float h, float d);

    public boolean getTransform(PageTransform out, float progress, float width, float height,
            float pageDistance, boolean isRtl) {
        out.reset(width, height);
        if (this == NONE || progress == 0f) {
            return false;
        }
        apply(out, isRtl ? -progress : progress, width, height, pageDistance);
        if (isRtl) {
            out.mirror(width);
        }
        return true;
    }

    public static PageTransitionEffect fromValue(String value) {
        for (PageTransitionEffect effect : values()) {
            if (effect.mValue.equals(value)) {
                return effect;
            }
        }
        return NONE;
    }

    public static class PageTransform {
        public float translationX;
        public float translationY;
        public float scaleX;
        public float scaleY;
        public float rotation;
        public float rotationX;
        public float rotationY;
        public float pivotX;
        public float pivotY;
        public float alpha;
        public boolean hasClip;
        public float clipLeft;
        public float clipRight;

        void reset(float width, float height) {
            translationX = 0f;
            translationY = 0f;
            scaleX = 1f;
            scaleY = 1f;
            rotation = 0f;
            rotationX = 0f;
            rotationY = 0f;
            pivotX = width / 2f;
            pivotY = height / 2f;
            alpha = 1f;
            hasClip = false;
            clipLeft = 0f;
            clipRight = width;
        }

        void mirror(float width) {
            translationX = -translationX;
            rotation = -rotation;
            rotationY = -rotationY;
            pivotX = width - pivotX;
            float left = clipLeft;
            clipLeft = width - clipRight;
            clipRight = width - left;
        }

        /** Moves the origin of the transform by {@code dx} along the x axis. */
        public void offsetX(float dx) {
            pivotX -= dx;
            clipLeft -= dx;
            clipRight -= dx;
        }

        public void getMatrix(Matrix out, Matrix tmp, Camera camera, float cameraBase) {
            out.setScale(scaleX, scaleY, pivotX, pivotY);
            if (rotationX != 0f || rotationY != 0f) {
                camera.save();
                camera.setLocation(0f, 0f,
                        -cameraBase * CAMERA_DISTANCE_FACTOR / CAMERA_UNITS_PER_INCH);
                camera.rotateX(rotationX);
                camera.rotateY(rotationY);
                camera.getMatrix(tmp);
                camera.restore();
                tmp.preTranslate(-pivotX, -pivotY);
                tmp.postTranslate(pivotX, pivotY);
                out.postConcat(tmp);
            }
            if (rotation != 0f) {
                out.postRotate(rotation, pivotX, pivotY);
            }
            out.postTranslate(translationX, translationY);
        }
    }
}
