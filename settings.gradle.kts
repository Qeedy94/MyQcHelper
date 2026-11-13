pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // 1. Объявляем плагин и его версию здесь (как в документации)
        id("com.android.settings") version "9.5.1" apply false
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    // 2. Применяем плагин здесь (без версии), чтобы активировать блок android
    id("com.android.settings")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// 3. Теперь блок android должен распознаваться
android {
    execution {
        profiles {
            create("highMemory") {
                r8 {
                    runInSeparateProcess = true
                    jvmOptions.addAll(listOf("-Xms2048m", "-Xmx8g"))
                }
            }
        }
    }
}

rootProject.name = "My Application"
include(":app")