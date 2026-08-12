package uk.darkbyte.deckscape;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import java.io.File;

/** Hands a verified update to Android's user-confirmed package installer. */
final class UpdateInstaller {
    private UpdateInstaller() {}

    static void install(Activity activity, File file) {
        if (!file.isFile() || !UpdateClient.UPDATE_FILE_NAME.equals(file.getName())) {
            throw new IllegalArgumentException("The verified update file is unavailable");
        }
        Uri uri = new Uri.Builder()
                .scheme("content")
                .authority(activity.getPackageName() + ".updates")
                .appendPath(UpdateClient.UPDATE_FILE_NAME)
                .build();
        Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        install.setData(uri);
        install.setClipData(ClipData.newRawUri("Deckscape update", uri));
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(install);
    }
}
