# Deckscape has no reflection-based model layer. Keep only the Android entry
# points instantiated from the manifest and allow R8 to optimize everything else.
-keep public class uk.darkbyte.deckscape.MainActivity { public <init>(); }
-keep public class uk.darkbyte.deckscape.WallpaperEngineService { public <init>(); }
# The BYD workaround checks the direct caller of this framework override.
# Preserve the stack boundary in minified builds as well as debug builds.
-keepclassmembers class uk.darkbyte.deckscape.WallpaperEngineService {
    public android.content.ContentResolver getContentResolver();
}
-keep public class uk.darkbyte.deckscape.UpdateFileProvider { public <init>(); }
