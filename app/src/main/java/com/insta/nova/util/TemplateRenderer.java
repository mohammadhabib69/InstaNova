package com.insta.nova.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import com.insta.nova.data.local.TemplateCatalog;

/**
 * Draws a template card to a {@link Bitmap}. Pure Canvas work, so the result is
 * identical on every device and needs no layout pass: the bitmap is what gets
 * handed to the existing New Post / New Story share paths.
 */
public final class TemplateRenderer {

    private static final int POST_WIDTH = 1080;
    private static final int POST_HEIGHT = 1350;
    private static final int STORY_WIDTH = 1080;
    private static final int STORY_HEIGHT = 1920;

    private TemplateRenderer() {}

    public static Bitmap render(TemplateCatalog.Template template, String[] values,
                                String photoPath, boolean story) {
        return render(template, values, photoPath,
                story ? STORY_WIDTH : POST_WIDTH,
                story ? STORY_HEIGHT : POST_HEIGHT);
    }

    /**
     * Renders at an explicit size. The story viewer crops to fill, so the caller can
     * pass the device aspect ratio to keep the card's type away from the edges.
     */
    public static Bitmap render(TemplateCatalog.Template template, String[] values,
                                String photoPath, int width, int height) {
        if (template == null || width <= 0 || height <= 0) return null;
        // A tall canvas gets the story layout (bigger type, photo lower).
        boolean story = height > width * 1.4f;

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        // Background: accent fading into near-black so white type always reads.
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, width, height,
                template.accentColor, 0xFF0B0B0B, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, bg);
        bg.setShader(null);

        Bitmap photo = decode(photoPath);
        float photoBottom = story ? height * 0.58f : height * 0.52f;
        if (photo != null) {
            drawCenterCrop(canvas, photo, new RectF(0, 0, width, photoBottom));
            // Scrim so the badge and title stay legible over bright photos.
            Paint scrim = new Paint(Paint.ANTI_ALIAS_FLAG);
            scrim.setShader(new LinearGradient(0, photoBottom * 0.45f, 0, photoBottom,
                    0x00000000, 0xFF0B0B0B, Shader.TileMode.CLAMP));
            canvas.drawRect(0, photoBottom * 0.45f, width, photoBottom, scrim);
            photo.recycle();
        }

        float left = 88f;
        float textWidth = width - left * 2;

        // Badge chip.
        Paint chip = new Paint(Paint.ANTI_ALIAS_FLAG);
        chip.setColor(0x33FFFFFF);
        RectF chipRect = new RectF(left, story ? 180f : 96f, left + 300f, (story ? 180f : 96f) + 62f);
        canvas.drawRoundRect(chipRect, 31f, 31f, chip);

        TextPaint badgePaint = textPaint(28f, Color.WHITE, Typeface.BOLD);
        badgePaint.setLetterSpacing(0.12f);
        canvas.drawText(template.badge, chipRect.left + 26f, chipRect.centerY() + 10f, badgePaint);

        float titleTop = photoBottom + (story ? 80f : 56f);
        TextPaint titlePaint = textPaint(story ? 84f : 72f, Color.WHITE, Typeface.BOLD);
        StaticLayout title = layout(template.title, titlePaint, (int) textWidth);
        canvas.save();
        canvas.translate(left, titleTop);
        title.draw(canvas);
        canvas.restore();

        float y = titleTop + title.getHeight() + (story ? 46f : 30f);
        String first = value(values, 0);
        if (!first.isEmpty()) {
            TextPaint firstPaint = textPaint(story ? 52f : 46f, 0xFFFFFFFF, Typeface.NORMAL);
            StaticLayout body = layout(first, firstPaint, (int) textWidth);
            canvas.save();
            canvas.translate(left, y);
            body.draw(canvas);
            canvas.restore();
            y += body.getHeight() + 22f;
        }

        String second = value(values, 1);
        if (!second.isEmpty()) {
            TextPaint secondPaint = textPaint(story ? 44f : 38f, 0xD9FFFFFF, Typeface.NORMAL);
            StaticLayout body = layout(second, secondPaint, (int) textWidth);
            canvas.save();
            canvas.translate(left, y);
            body.draw(canvas);
            canvas.restore();
        }

        // Footer wordmark, kept subtle.
        TextPaint footer = textPaint(30f, 0x99FFFFFF, Typeface.BOLD);
        footer.setLetterSpacing(0.06f);
        canvas.drawText("InstaNova", left, height - 64f, footer);

        return output;
    }

    private static String value(String[] values, int index) {
        if (values == null || index >= values.length || values[index] == null) return "";
        return values[index].trim();
    }

    private static TextPaint textPaint(float size, int color, int style) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setTypeface(Typeface.create("sans-serif", style));
        return paint;
    }

    private static StaticLayout layout(String text, TextPaint paint, int width) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, Math.max(1, width))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(6f, 1f)
                .setIncludePad(false)
                .build();
    }

    /** Center-crops {@code bitmap} into {@code dst}. */
    private static void drawCenterCrop(Canvas canvas, Bitmap bitmap, RectF dst) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) return;
        float scale = Math.max(dst.width() / bitmap.getWidth(), dst.height() / bitmap.getHeight());
        float drawnWidth = bitmap.getWidth() * scale;
        float drawnHeight = bitmap.getHeight() * scale;
        float left = dst.left - (drawnWidth - dst.width()) / 2f;
        float top = dst.top - (drawnHeight - dst.height()) / 2f;

        Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        RectF target = new RectF(left, top, left + drawnWidth, top + drawnHeight);
        canvas.save();
        canvas.clipRect(dst);
        canvas.drawBitmap(bitmap, src, target, null);
        canvas.restore();
    }

    /** Decodes with a sample size so a 12MP gallery photo cannot exhaust memory. */
    private static Bitmap decode(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= 1080 && bounds.outHeight / (sample * 2) >= 1080) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, options);
        } catch (OutOfMemoryError | Exception e) {
            return null;
        }
    }
}
