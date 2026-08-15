package uk.darkbyte.deckscape;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.IOException;

/** Measures a small local decode; no image data leaves the device. */
final class WallpaperLuminanceClassifier {
    private static final int MAX_SAMPLE_AXIS = 96;

    private WallpaperLuminanceClassifier() {}

    static double measure(File file) throws IOException {
        if (file == null || !file.isFile()) throw new IOException("Wallpaper is unavailable");
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Wallpaper brightness could not be measured");
        }
        int sample = 1;
        while (bounds.outWidth / sample > MAX_SAMPLE_AXIS
                || bounds.outHeight / sample > MAX_SAMPLE_AXIS) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) throw new IOException("Wallpaper brightness could not be measured");
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            return meanLuminance(pixels);
        } finally {
            bitmap.recycle();
        }
    }

    static double meanLuminance(int[] pixels) {
        if (pixels == null || pixels.length == 0) return 0.5;
        double total = 0;
        int count = 0;
        for (int pixel : pixels) {
            if ((pixel >>> 24) < 128) continue;
            int red = (pixel >>> 16) & 0xff;
            int green = (pixel >>> 8) & 0xff;
            int blue = pixel & 0xff;
            total += (0.2126 * red + 0.7152 * green + 0.0722 * blue) / 255.0;
            count++;
        }
        return count == 0 ? 0.5 : total / count;
    }
}
