package com.insta.clone.util;

import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * Instagram-style photo filter presets implemented using ColorMatrix matrices.
 */
public final class ColorMatrixPresets {

    public static class FilterPreset {
        public final String name;
        public final float[] matrix;

        public FilterPreset(String name, float[] matrix) {
            this.name = name;
            this.matrix = matrix;
        }

        public ColorMatrixColorFilter getColorFilter(float intensity) {
            if (intensity >= 1.0f) {
                return new ColorMatrixColorFilter(new ColorMatrix(matrix));
            }
            // Interpolate between Identity and Preset matrix based on intensity
            float[] identity = new ColorMatrix().getArray();
            float[] interpolated = new float[20];
            for (int i = 0; i < 20; i++) {
                interpolated[i] = identity[i] + (matrix[i] - identity[i]) * intensity;
            }
            return new ColorMatrixColorFilter(new ColorMatrix(interpolated));
        }
    }

    public static List<FilterPreset> getPresets() {
        List<FilterPreset> list = new ArrayList<>();

        // Normal (Identity)
        list.add(new FilterPreset("Normal", new float[]{
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
        }));

        // Clarendon (High contrast, boosted blues and warmth)
        list.add(new FilterPreset("Clarendon", new float[]{
                1.2f, 0f, 0f, 0f, -10f,
                0f, 1.15f, 0f, 0f, -5f,
                0f, 0f, 1.35f, 0f, 15f,
                0f, 0f, 0f, 1f, 0f
        }));

        // Gingham (Vintage faded look)
        list.add(new FilterPreset("Gingham", new float[]{
                1.05f, 0.05f, 0f, 0f, 15f,
                0f, 1.0f, 0.05f, 0f, 15f,
                0.05f, 0f, 0.95f, 0f, 20f,
                0f, 0f, 0f, 1f, 0f
        }));

        // Moon (Black and white high brightness)
        list.add(new FilterPreset("Moon", new float[]{
                0.33f, 0.33f, 0.33f, 0f, 10f,
                0.33f, 0.33f, 0.33f, 0f, 10f,
                0.33f, 0.33f, 0.33f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
        }));

        // Lark (Bright, desaturated reds, vibrant greens and blues)
        list.add(new FilterPreset("Lark", new float[]{
                1.1f, 0f, 0f, 0f, 5f,
                0f, 1.25f, 0f, 0f, 10f,
                0f, 0f, 1.25f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
        }));

        // Reyes (Warm, dusty vintage)
        list.add(new FilterPreset("Reyes", new float[]{
                1.15f, 0.1f, 0f, 0f, 25f,
                0f, 1.05f, 0f, 0f, 20f,
                0f, 0f, 0.9f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
        }));

        // Juno (Deepened shadows, warm tones)
        list.add(new FilterPreset("Juno", new float[]{
                1.2f, 0f, 0f, 0f, 10f,
                0f, 1.1f, 0.1f, 0f, 5f,
                0f, 0f, 1.0f, 0f, -5f,
                0f, 0f, 0f, 1f, 0f
        }));

        // Slumber (Soft desaturated dreamy warmth)
        list.add(new FilterPreset("Slumber", new float[]{
                0.95f, 0.1f, 0f, 0f, 15f,
                0.05f, 0.95f, 0f, 0f, 10f,
                0f, 0.05f, 0.9f, 0f, 20f,
                0f, 0f, 0f, 1f, 0f
        }));

        return list;
    }
}
