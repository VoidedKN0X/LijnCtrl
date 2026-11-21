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

# WorkManager keeps its job list in a Room 2.6.1 database, and Room creates the generated
# WorkDatabase_Impl by reflection through its no-argument constructor. Room 2.6.1's own rule
# (-keep class * extends androidx.room.RoomDatabase) names no members, and the R8 in Android
# Gradle Plugin 9 no longer keeps a constructor for such a rule, so without this line the app
# would crash at start (NoSuchMethodException: WorkDatabase_Impl.<init>). Same rule Room 2.7 ships.
-keep class * extends androidx.room.RoomDatabase { void <init>(); }
