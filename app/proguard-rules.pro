# Reglas ProGuard / R8 para Aréte Social

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

# H-14 / ARETE-LOG-001 / SEC-07: Eliminar logs en builds de release para evitar fugar IDs de usuario, MACs y trazas
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# OSMDroid (migración desde Google Maps)
-dontwarn org.osmdroid.**

# Google ErrorProne annotations (Tink / Security-Crypto)
-dontwarn com.google.errorprone.annotations.**

