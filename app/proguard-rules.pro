# kotlinx.serialization models (library ships its own rules; these keep our DTOs and routes intact).
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep,includedescriptorclasses class com.vknxworks.lijnctrl.**$$serializer { *; }
-keepclassmembers class com.vknxworks.lijnctrl.** {
    *** Companion;
}
-keepclasseswithmembers class com.vknxworks.lijnctrl.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Home-screen widget: the receiver, config screen and refresh action are created by Android and Glance from their names.
-keep class com.vknxworks.lijnctrl.widget.** { *; }

# osmdroid (OpenStreetMap map in Nearby)
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# No debug, verbose or info logging in release builds (LijnCtrl's own code doesn't log; this also silences
# libraries such as osmdroid, which otherwise logs map and tile details).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
