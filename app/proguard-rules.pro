# CountAI ProGuard rules

# TensorFlow Lite (uses reflection for delegates)
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-keep class org.tensorflow.lite.nnapi.** { *; }
-dontwarn org.tensorflow.lite.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Room — keep entities, DAOs and the database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class com.zenithai.countai.data.** { *; }

# App model classes
-keep class com.zenithai.countai.detection.** { *; }

# kotlinx.serialization — keep generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }

# supabase-kt / Ktor (reflection-heavy HTTP client)
-keep class io.github.jan_tennert.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Sync DTOs must survive shrinking (field names map to DB columns)
-keep class com.zenithai.countai.sync.** { *; }
