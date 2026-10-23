# Proguard rules for QuickTiles
-keep class dev.rikka.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }
-keepclassmembers class rikka.shizuku.Shizuku {
    private static *** newProcess(...);
    public static *** *;
}

