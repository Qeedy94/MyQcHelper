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
        versionCode = 8
        versionName = "1.7.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8 (минификация) выключен: правила для POI/Gson уже лежат в proguard-rules.pro,
            // перед включением нужно проверить release-сборку на устройстве
            isMinifyEnabled = false
            // Сжатие ресурсов работает только вместе с минификацией
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
    // Нужен для иконок вне базового набора (PhotoCamera, Visibility, UploadFile и др.)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    // Тема Theme.SplashScreen в themes.xml
    implementation(libs.androidx.core.splashscreen)
    // Работа с папками через SAF (DocumentFile)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Отображение картинок (Coil 3)
    implementation(libs.coil.compose)
    // Сохранение заказов в SharedPreferences (JSON)
    implementation(libs.gson)
    // Чтение Excel
    implementation(libs.poi.ooxml)
    implementation("androidx.appcompat:appcompat:1.6.1")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
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