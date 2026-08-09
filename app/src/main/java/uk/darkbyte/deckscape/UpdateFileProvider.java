package uk.darkbyte.deckscape;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Exposes only the verified cached update APK to Android's package installer. */
public final class UpdateFileProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        requireUpdateUri(uri);
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = requireUpdateUri(uri);
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) row.add(UpdateClient.UPDATE_FILE_NAME);
            else if (OpenableColumns.SIZE.equals(column)) row.add(file.length());
            else row.add(null);
        }
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("The update is read-only");
        File file = requireUpdateUri(uri);
        if (!file.isFile()) throw new FileNotFoundException("The verified update is unavailable");
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("The update provider is read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }

    private File requireUpdateUri(Uri uri) {
        if (getContext() == null) throw new IllegalStateException("Provider is unavailable");
        String authority = getContext().getPackageName() + ".updates";
        if (!"content".equals(uri.getScheme()) || !authority.equals(uri.getAuthority())
                || uri.getPathSegments().size() != 1
                || !UpdateClient.UPDATE_FILE_NAME.equals(uri.getLastPathSegment())) {
            throw new IllegalArgumentException("Unsupported update URI");
        }
        File directory = new File(getContext().getCacheDir(), "updates");
        File file = new File(directory, UpdateClient.UPDATE_FILE_NAME);
        try {
            String directoryPath = directory.getCanonicalPath() + File.separator;
            if (!file.getCanonicalPath().startsWith(directoryPath)) {
                throw new IllegalArgumentException("Update path escaped its cache directory");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to resolve the update path", exception);
        }
        return file;
    }
}
