plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
}

import java.util.Properties

android {
    namespace = "com.zenithai.countai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zenithai.countai"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "2.1.0"

        // Supabase credentials come from local.properties (never committed).
        // Empty = cloud features stay off; the app runs fully offline.
        val localProps = Properties()
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localPropsFile.inputStream().use { localProps.load(it) }
        }
        val supabaseUrl: String = localProps.getProperty("SUPABASE_URL", "")
        val supabaseAnonKey: String = localProps.getProperty("SUPABASE_ANON_KEY", "")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.fragment:fragment-ktx:1.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.6")

    // CameraX
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // TensorFlow Lite — on-device object detection (no API keys, works offline)
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")

    // Room — counting history (kapt; KSP unneeded)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // WorkManager — background cloud sync (battery-friendly, constrained)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Supabase (auth + postgrest + storage) — sync target only.
    // Room stays the source of truth; the app works fully offline.
    // PINNED: supabase-kt 3.0.0 + ktor 3.0.3 are the newest releases built
    // with Kotlin 2.0 metadata, which this project's Kotlin 2.0.20 compiler
    // can read. Newer releases (supabase-kt 3.1+, metadata 2.1/2.2/2.4)
    // break compilation AND break Room's kapt processor. Do not bump
    // without also upgrading the project's Kotlin version.
    val supabaseVersion = "3.0.0"
    implementation(platform("io.github.jan-tennert.supabase:bom:$supabaseVersion"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.ktor:ktor-client-android:3.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

// supabase-kt's auth pulls androidx.browser:1.10.0 transitively, which
// demands compileSdk 36. We compile against 34, so force the last 34-safe
// release. (Only used for OAuth browser tabs; harmless.)
configurations.all {
    resolutionStrategy {
        force("androidx.browser:browser:1.8.0")
    }
}
