# Contributing

Contributions are welcome. Keep the application safe for low-powered Android
head units and avoid adding wallpaper artwork to the APK.

Before opening a pull request:

1. Run `./gradlew test lintDebug assembleDebug` (or `gradlew.bat` on Windows).
2. Add unit tests for parsing, URL validation, rotation, or other pure logic.
3. Keep network destinations explicit and HTTPS-only.
4. Do not add analytics, credentials, signing keys, private device evidence,
   or images without clear redistribution rights.
5. Explain any memory, CPU, or bandwidth impact relevant to a head unit.

The codebase intentionally uses the Android framework without a large UI or
image-loading dependency. Proposals to add dependencies should include the APK
size and runtime benefit.
