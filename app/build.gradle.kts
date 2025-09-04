import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.ksp)
}
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

val githubTokenSupabase = localProperties.getProperty("GITHUB_TOKEN_SUPABASE") ?: ""

android {
    namespace = "com.dominikbaki.treuebiss"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dominikbaki.treuebiss"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "GITHUB_TOKEN_SUPABASE", "\"$githubTokenSupabase\"")
        }
        release {
            buildConfigField("String", "GITHUB_TOKEN_SUPABASE", "\"$githubTokenSupabase\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    // Core & UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Hilt - Dependency Injection
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.room.common.jvm)
    ksp(libs.hilt.compiler)

    // Navigation - Jetpack Compose
    implementation(libs.androidx.navigation.compose)

    // Kotlinx - Serialization
    implementation(libs.kotlinx.serialization.json)

    // Room - Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Datastore - Persistent Daten
    implementation(libs.androidx.datastore.preferences)

    // Retrofit - Network für Wetter-API
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.logging.interceptor)

    // Supabase - Backend-as-s-Service
//    implementation(platform(libs.supabase.bom))
//    implementation(libs.supabase.gotrue)
//    implementation(libs.supabase.postgrest)
//    implementation(libs.supabase.realtime)
//    implementation(libs.supabase.storage)

    // Coroutines - Für async/await
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Test-Abhänigkeiten
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}