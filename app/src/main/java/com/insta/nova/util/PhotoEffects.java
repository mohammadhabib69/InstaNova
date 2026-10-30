package com.insta.nova.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.io.ByteArrayOutputStream;

/**
 * The editor's photo pipeline: filter preset + intensity + the eight adjust tools,
 * composed into one {@link ColorMatrixColorFilter} so the preview and the uploaded
 * bytes are produced from the same numbers.
 *
 * <p>Vignette is not expressible as a colour matrix, so it is drawn as a radial
 * overlay while baking; the preview approximates it the same way.
 */
public final class PhotoEffects {

    public static final String[] TOOLS = {
            "Brightness", "Contrast", "Saturation", "Warmth",
            "Fade", "Highlights", "Shadows", "Vignette"
    };

    /** Adjustments are -1..1 with 0 neutral, except vignette where 0 is "off". */
    public static final int VIGNETTE_INDEX = 7;

    private PhotoEffects() {}

    public static ColorMatrixColorFilter build(ColorMatrixPresets.FilterPreset preset,
                                               float intensity, float[] adjust) {
        ColorMatrix matrix = new ColorMatrix();

        if (preset != null && intensity > 0f) {
            float[] identity = new ColorMatrix().getArray();
            float[] blended = new float[20];
            for (int i = 0; i < 20; i++) {
                blended[i] = identity[i] + (preset.matrix[i] - identity[i]) * intensity;
            }
            matrix.set(blended);
        }

        if (adjust == null) return new ColorMatrixColorFilter(matrix);

        float brightness = adjust[0];
        float contrast = adjust[1];
        float saturation = adjust[2];
        float warmth = adjust[3];
        float fade = adjust[4];
        float highlights = adjust[5];
        float shadows = adjust[6];

        if (brightness != 0f) matrix.postConcat(offset(brightness * 80f));
        if (contrast != 0f) matrix.postConcat(scaleAroundMid(1f + contrast * 0.8f));
        if (saturation != 0f) matrix.postConcat(saturationMatrix(1f + saturation));
        if (warmth != 0f) {
            matrix.postConcat(channelOffset(warmth * 36f, warmth * 6f, -warmth * 36f));
        }
        if (fade != 0f) {
            // Fade lifts the black point and compresses the top end.
            matrix.postConcat(scaleAroundMid(1f - fade * 0.35f));
            matrix.postConcat(offset(fade * 46f));
        }
        if (highlights != 0f) {
            // Highlights lean on the top half: a gentle gain plus a matching offset.
            matrix.postConcat(scaleAroundMid(1f + highlights * 0.28f));
            matrix.postConcat(offset(-highlights * 18f));
        }
        if (shadows != 0f) {
            matrix.postConcat(scaleAroundMid(1f - shadows * 0.18f));
            matrix.postConcat(offset(shadows * 30f));
        }
        return new ColorMatrixColorFilter(matrix);
    }

    private static ColorMatrix offset(float amount) {
        ColorMatrix m = new ColorMatrix();
        m.set(new float[]{
                1, 0, 0, 0, amount,
                0, 1, 0, 0, amount,
                0, 0, 1, 0, amount,
                0, 0, 0, 1, 0
        });
        return m;
    }

    private static ColorMatrix channelOffset(float r, float g, float b) {
        ColorMatrix m = new ColorMatrix();
        m.set(new float[]{
                1, 0, 0, 0, r,
                0, 1, 0, 0, g,
                0, 0, 1, 0, b,
                0, 0, 0, 1, 0
        });
        return m;
    }

    private static ColorMatrix scaleAroundMid(float scale) {
        float translate = 128f * (1f - scale);
        ColorMatrix m = new ColorMatrix();
        m.set(new float[]{
                scale, 0, 0, 0, translate,
                0, scale, 0, 0, translate,
                0, 0, scale, 0, translate,
                0, 0, 0, 1, 0
        });
        return m;
    }

    private static ColorMatrix saturationMatrix(float s) {
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(s);
        return m;
    }

    /**
     * Re-encodes the picked JPEG with the current look baked in, so what the editor
     * showed is what gets stored.
     */
    public static byte[] bake(byte[] source, ColorMatrixColorFilter filter, float vignette) {
        if (source == null || source.length == 0) return source;
        Bitmap decoded = BitmapFactory.decodeByteArray(source, 0, source.length);
        if (decoded == null) return source;

        Bitmap out = Bitmap.createBitmap(decoded.getWidth(), decoded.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        if (filter != null) paint.setColorFilter(filter);
        canvas.drawBitmap(decoded, 0f, 0f, paint);

        if (vignette > 0.01f) {
            float w = out.getWidth();
            float h = out.getHeight();
            float radius = (float) Math.sqrt(w * w + h * h) / 2f;
            Paint overlay = new Paint(Paint.ANTI_ALIAS_FLAG);
            overlay.setShader(new RadialGradient(w / 2f, h / 2f, radius,
                    new int[]{0x00000000, 0x00000000, ((int) (vignette * 190) << 24)},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, overlay);
        }
        decoded.recycle();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        out.compress(Bitmap.CompressFormat.JPEG, 92, buffer);
        out.recycle();
        return buffer.toByteArray();
    }

    /** Human label for a slider position: the middle is neutral. */
    public static String labelFor(String tool, int progress) {
        int delta = progress - 100;
        if (delta == 0) return tool + " 0";
        return tool + " " + (delta > 0 ? "+" : "") + delta;
    }
}
