# Proguard rules for QuickTiles

# Shizuku / Shevery IPC
-keep class dev.rikka.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keepclassmembers class rikka.shizuku.Shizuku {
    private static *** newProcess(...);
    public static *** *;
}

# AndroidSVG
-keep class com.caverock.androidsvg.** { *; }

# Coil
-keep class coil.** { *; }

# QuickSetTiles Core packages
-keep class com.wizzwuz.quicksettiles.QuickTilesApp { *; }
-keep class com.wizzwuz.quicksettiles.model.** { *; }
-keep class com.wizzwuz.quicksettiles.data.** { *; }
-keep class com.wizzwuz.quicksettiles.service.** { *; }
-keep class com.wizzwuz.quicksettiles.ui.** { *; }
-keep class com.wizzwuz.quicksettiles.util.** { *; }

# Suppress warnings from third-party libraries so R8 doesn't abort
-dontwarn **

# Keep attributes
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature,SourceFile,LineNumberTable

