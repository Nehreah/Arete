# Reglas ProGuard / R8 para AntiRed Social

# Kotlin Serialization
-keepattributes *Annotation*,ElementValueAttribute,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class **$serializer {
    *** INSTANCE;
}
-keepclassmembers class * {
    *** Companion;
}

# Modelos de dominio y DTOs serializables de Aréte
-keep class com.renea.arete_social.domain.model.** { *; }
-keep class com.renea.arete_social.domain.mesh.** { *; }

# H-14: Eliminar logs de depuración (verbose/debug) en builds de release para evitar fugar IDs de usuario y MACs
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# OSMDroid (migración desde Google Maps)
-dontwarn org.osmdroid.**

# Google ErrorProne annotations (Tink / Security-Crypto)
-dontwarn com.google.errorprone.annotations.**

