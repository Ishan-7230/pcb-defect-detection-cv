# Industry 4.0 Visual Inspection ProGuard / R8 Rules

# Preserve ONNX Runtime classes and JNI bindings
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Preserve Room Database entities and DAOs
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Preserve Data Models and Detections for serialization/deserialization
-keep class com.industry4.inspection.ai.** { *; }
-keep class com.industry4.inspection.data.models.** { *; }
-keep class com.industry4.inspection.data.database.** { *; }

# Strip verbose logging in release builds to prevent information leakage
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Preserve Compose runtime annotations
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
