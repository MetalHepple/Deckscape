# HorizonDeck has no reflection-based model layer. Keep wallpaper service names
# because the Android framework instantiates them from the manifest.
-keep public class uk.darkbyte.horizondeck.MainActivity { public <init>(); }
-keep public class uk.darkbyte.horizondeck.WallpaperEngineService { public <init>(); }
