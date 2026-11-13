plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.myapplication"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "1.7.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Включаем R8 (минификацию) по старинке
            isMinifyEnabled = false
            // Включаем сжатие ресурсов (рекомендуется вместе с минификацией)
            isShrinkResources = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug" // 👈 Добавит .debug к ID
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("androidx.activity:activity-compose:1.8.2")
    // Библиотека для чтения Excel
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    // Библиотека для работы с файлами
    implementation("androidx.documentfile:documentfile:1.0.1")
    // Библиотека для распознавания текста на картинках (OCR)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // Библиотека для красивого отображения картинок (Coil)
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
}
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            // Берем versionName из output, а не из variant
            val versionName = output.versionName.get() ?: "1.0"
            val outputImpl = output as com.android.build.api.variant.impl.VariantOutputImpl
            outputImpl.outputFileName.set("Punta_destroyer_v${versionName}.apk")
        }
    }
}