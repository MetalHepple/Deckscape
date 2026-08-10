package uk.darkbyte.deckscape;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Movie;

import java.io.File;
import java.io.IOException;

/** Decodes a bounded, aspect-preserving local source for the wallpaper options preview. */
final class WallpaperPreviewDecoder {
    private static final int MAX_PREVIEW_AXIS = 2_048;

    private WallpaperPreviewDecoder() {}

    static Decoded decode(File file) throws IOException {
        if (file == null || !file.isFile()) throw new IOException("Wallpaper is unavailable");
        if (WallpaperRules.isGif(file.getName())) {
            Movie movie = Movie.decodeFile(file.getAbsolutePath());
            if (movie == null || movie.width() <= 0 || movie.height() <= 0) {
                throw new IOException("GIF preview could not be decoded");
            }
            return new Decoded(null, movie);
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Image preview could not be decoded");
        }
        int sample = 1;
        while (bounds.outWidth / sample > MAX_PREVIEW_AXIS
                || bounds.outHeight / sample > MAX_PREVIEW_AXIS) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) throw new IOException("Image preview could not be decoded");
        return new Decoded(bitmap, null);
    }

    /** Exactly one decoded representation suitable for the preview surface. */
    static final class Decoded {
        final Bitmap bitmap;
        final Movie movie;

        Decoded(Bitmap bitmap, Movie movie) {
            this.bitmap = bitmap;
            this.movie = movie;
        }
    }
}
