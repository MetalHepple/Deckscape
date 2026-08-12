package uk.darkbyte.deckscape;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Verifies package identity, version, and signing continuity before installation. */
final class UpdateVerifier {
    /** Public SHA-256 fingerprint of Deckscape's dedicated production signing certificate. */
    private static final String RELEASE_CERTIFICATE_SHA256 =
            "E4B51F59954C622CA188B4BCD4E6BCE87112E903350FB4659211AD4F981238D4";

    static final class Result {
        final File file;
        final String versionName;
        final long versionCode;

        Result(File file, String versionName, long versionCode) {
            this.file = file;
            this.versionName = versionName;
            this.versionCode = versionCode;
        }
    }

    private UpdateVerifier() {}

    @SuppressWarnings("deprecation")
    static Result verify(Context context, File file, String expectedVersion) throws IOException {
        if (!file.isFile() || file.length() <= 0 || file.length() > UpdateRules.MAX_APK_BYTES) {
            throw new IOException("The downloaded update file is invalid");
        }
        PackageManager manager = context.getPackageManager();
        PackageInfo candidate = manager.getPackageArchiveInfo(file.getAbsolutePath(),
                PackageManager.GET_SIGNING_CERTIFICATES | PackageManager.GET_SIGNATURES);
        if (candidate == null) throw new IOException("Android could not read the update APK");
        if (!context.getPackageName().equals(candidate.packageName)) {
            throw new IOException("The update APK has the wrong package name");
        }
        if (candidate.versionName == null
                || UpdateVersion.compare(candidate.versionName, expectedVersion) != 0) {
            throw new IOException("The update APK version does not match its release");
        }
        long versionCode = candidate.getLongVersionCode();
        if (versionCode <= BuildConfig.VERSION_CODE) {
            throw new IOException("The update APK is not newer than this installation");
        }

        PackageInfo installed;
        try {
            installed = manager.getPackageInfo(context.getPackageName(),
                    PackageManager.GET_SIGNING_CERTIFICATES | PackageManager.GET_SIGNATURES);
        } catch (PackageManager.NameNotFoundException exception) {
            throw new IOException("Deckscape's installed package could not be verified", exception);
        }
        Set<String> installedSigners = signerDigests(installed);
        Set<String> candidateSigners = signerDigests(candidate);
        if (installedSigners.isEmpty() || candidateSigners.isEmpty()) {
            throw new IOException("Android could not read the APK signing certificate");
        }
        if (!installedSigners.equals(candidateSigners)) {
            throw new IOException(BuildConfig.DEBUG
                    ? "This test build uses a different signing key from the release APK"
                    : "The update APK signing certificate does not match Deckscape");
        }
        if (!BuildConfig.DEBUG
                && (!installedSigners.contains(RELEASE_CERTIFICATE_SHA256)
                || !candidateSigners.contains(RELEASE_CERTIFICATE_SHA256))) {
            throw new IOException("The update APK is not signed by Deckscape's release key");
        }
        return new Result(file, UpdateVersion.normalize(candidate.versionName), versionCode);
    }

    /** Reads modern signing metadata, with a fallback for incomplete vendor PackageManager builds. */
    @SuppressWarnings("deprecation")
    private static Set<String> signerDigests(PackageInfo info) throws IOException {
        Set<String> result = new HashSet<>();
        Signature[] signatures = info.signingInfo == null
                ? null : info.signingInfo.getApkContentsSigners();
        if ((signatures == null || signatures.length == 0) && info.signatures != null) {
            signatures = info.signatures;
        }
        if (signatures == null) return result;
        for (Signature signature : signatures) result.add(sha256(signature.toByteArray()));
        return result;
    }

    private static String sha256(byte[] value) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder builder = new StringBuilder(64);
            for (byte item : digest.digest(value)) {
                builder.append(String.format(Locale.ROOT, "%02X", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IOException("Unable to verify the APK signing certificate", exception);
        }
    }
}
