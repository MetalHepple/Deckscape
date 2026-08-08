# Deckscape has no reflection-based model layer. Keep only the Android entry
# points instantiated from the manifest and allow R8 to optimize everything else.
-keep public class uk.darkbyte.deckscape.MainActivity { public <init>(); }
-keep public class uk.darkbyte.deckscape.WallpaperEngineService { public <init>(); }
