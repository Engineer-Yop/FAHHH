package com.example.sanivend;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Product photos on the free Firebase plan: the photo the owner picks is shrunk to
 * at most 320 px and saved as a small JPEG (about 15–30 KB) in Base64 text at
 * /devices/{id}/product_images/tray_N (not under /trays, which the ESP32 downloads).
 * No Firebase Storage needed.
 */
public final class ProductImages {

    /** Longest side of a saved photo, in pixels. */
    static final int MAX_SIDE = 320;
    static final int JPEG_QUALITY = 75;

    private ProductImages() {}

    /** Reads the picked photo, shrinks it and returns it as Base64 JPEG text. Run off the main thread. */
    public static String encode(ContentResolver resolver, Uri uri) throws IOException {
        Bitmap bitmap = load(resolver, uri);
        if (bitmap == null) throw new IOException("This photo can't be opened");
        Bitmap small = scaleDown(bitmap, MAX_SIDE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        small.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    /** Base64 JPEG text from Firebase → picture, or null if missing or broken. */
    public static Bitmap decode(Object base64) {
        if (base64 == null) return null;
        String s = String.valueOf(base64);
        if (s.isEmpty()) return null;
        try {
            byte[] bytes = Base64.decode(s, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Bitmap load(ContentResolver resolver, Uri uri) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder also turns phone photos the right way up (EXIF rotation)
            ImageDecoder.Source source = ImageDecoder.createSource(resolver, uri);
            return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                int longest = Math.max(w, h);
                if (longest > MAX_SIDE * 2) {
                    float k = (MAX_SIDE * 2f) / longest;
                    decoder.setTargetSize(Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k)));
                }
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        }
        // Android 7–8: read the size first, then load a reduced copy
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE * 2);
        try (InputStream in = resolver.openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, opts);
        }
    }

    static int sampleSize(int w, int h, int target) {
        int s = 1;
        while (Math.max(w, h) / (s * 2) >= target) s *= 2;
        return s;
    }

    private static Bitmap scaleDown(Bitmap b, int maxSide) {
        int w = b.getWidth(), h = b.getHeight();
        int longest = Math.max(w, h);
        if (longest <= maxSide) return b;
        float k = (float) maxSide / longest;
        return Bitmap.createScaledBitmap(b, Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k)), true);
    }
}
