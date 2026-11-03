plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Modul koneksi jam Huawei/Honor, diambil dari JoyoFit (AGPL-3.0-or-later, turunan Gadgetbridge; lihat LICENSE dan NOTICE
// di folder ini). Lapisan Expo/React Native dibuang; database memakai SQLite bawaan Android.
android {
    namespace = "com.joyodigitama.huaweiwear"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        buildConfigField("String", "DISTRIBUTION", "\"sideload\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.health.connect:connect-client:1.1.0")
    // ZIP berenkripsi AES (ekspor Privacy Centre Huawei); Apache-2.0.
    implementation("net.lingala.zip4j:zip4j:2.11.5")
}
