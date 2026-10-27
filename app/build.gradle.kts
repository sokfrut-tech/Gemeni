plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.protobuf")
}

android {
    namespace = "com.jarvis.breeno.assistant"
    compileSdk = 35
    ndkVersion = "26.3.11579264"
    defaultConfig {
        applicationId = "com.jarvis.breeno.assistant"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    buildFeatures { compose = true; aidl = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.31.6" } }
    packaging { jniLibs { useLegacyPackaging = true } }
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:4.29.3" }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") { option("lite") }
            }
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    // Sherpa-ONNX v2.0.1 supplies the matching native ONNX Runtime 1.28.2.
    // Do not add a second onnxruntime-android Maven dependency: mixing ORT builds
    // can cause duplicate native libraries and ABI/runtime mismatches.
    implementation("com.google.protobuf:protobuf-javalite:4.29.3")
    implementation("com.google.code.gson:gson:2.12.1")
}
