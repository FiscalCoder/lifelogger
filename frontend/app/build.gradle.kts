import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

// Read client config from environment or local.properties.
// Set `server.base.url=https://lifelogger.huecentral.cloud` and `api.token=...`
// in frontend/local.properties for local Android builds.
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { localProps.load(it) }
}
fun String.toBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val serverBaseUrl: String =
    System.getenv("LIFELOGGER_SERVER_BASE_URL")
        ?: localProps.getProperty("server.base.url")
        ?: "https://lifelogger.huecentral.cloud"
val apiToken: String =
    System.getenv("LIFELOGGER_API_TOKEN")
        ?: System.getenv("API_TOKEN")
        ?: localProps.getProperty("api.token")
        ?: ""

android {
    namespace = "com.lifelogger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lifelogger"
        minSdk = 30      // Android 11 — Qin F21 Pro target
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "SERVER_BASE_URL", serverBaseUrl.toBuildConfigString())
        buildConfigField("String", "API_TOKEN", apiToken.toBuildConfigString())
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
