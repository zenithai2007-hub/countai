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
