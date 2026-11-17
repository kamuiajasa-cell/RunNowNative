// Modul data murni (tanpa Android) untuk RunSense: JSON, ingest Intervals.icu dan jam, penyimpanan, cuaca, repository.
// Bagian Android (berkas, HTTP, lokasi, jam Huawei) ada di :app lewat antarmuka di Ports.kt.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":domain"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
