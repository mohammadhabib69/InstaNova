package com.insta.nova.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Image processing utilities for Instagram compression (1080px max edge, JPEG q85).
 */
public final class ImageUtils {

    private ImageUtils() {}

    private static final int MAX_LONG_EDGE_PX = 1080;
    private static final int JPEG_QUALITY = 85;

    public static byte[] compressImage(Bitmap bitmap) {
        if (bitmap == null) return new byte[0];

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scale = 1.0f;

        if (width > MAX_LONG_EDGE_PX || height > MAX_LONG_EDGE_PX) {
            if (width > height) {
                scale = (float) MAX_LONG_EDGE_PX / width;
            } else {
                scale = (float) MAX_LONG_EDGE_PX / height;
            }
        }

        Bitmap scaled;
        if (scale < 1.0f) {
            scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    Math.round(width * scale),
                    Math.round(height * scale),
                    true
            );
        } else {
            scaled = bitmap;
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream);
        return stream.toByteArray();
    }

    public static byte[] compressFileToBytes(File file) {
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) return new byte[0];

        // Check and apply EXIF orientation
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                default:
                    break;
            }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (Exception ignored) {}

        return compressImage(bitmap);
    }

    public static byte[] processUriToJpegBytes(android.content.Context context, android.net.Uri uri) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (java.io.InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) throw new IOException("Cannot open input stream for: " + uri);
            BitmapFactory.decodeStream(is, null, options);
        }

        int longEdge = Math.max(options.outWidth, options.outHeight);
        int inSampleSize = 1;
        while (longEdge / (inSampleSize * 2) >= MAX_LONG_EDGE_PX) {
            inSampleSize *= 2;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = inSampleSize;

        Bitmap bitmap;
        try (java.io.InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) throw new IOException("Cannot open input stream for: " + uri);
            bitmap = BitmapFactory.decodeStream(is, null, options);
        }
        if (bitmap == null) {
            throw new IOException("Failed to decode image bitmap from: " + uri);
        }

        try (java.io.InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is != null) {
                ExifInterface exif = new ExifInterface(is);
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                Matrix matrix = new Matrix();
                if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                    matrix.postRotate(90);
                } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                    matrix.postRotate(180);
                } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                    matrix.postRotate(270);
                }
                if (!matrix.isIdentity()) {
                    Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                    if (rotated != bitmap) {
                        bitmap.recycle();
                        bitmap = rotated;
                    }
                }
            }
        } catch (Exception ignored) {}

        byte[] result = compressImage(bitmap);
        bitmap.recycle();
        return result;
    }
}

