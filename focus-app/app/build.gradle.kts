plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Номер сборки из GitHub Actions — чтобы каждая новая версия ставилась поверх старой.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.focusguard.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.focusguard.app"
        minSdk = 30
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    // Один постоянный ключ подписи: без него каждое обновление требовало бы
    // удалять приложение и заново выдавать разрешения.
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/focusguard.jks")
            storePassword = "focusguard"
            keyAlias = "focusguard"
            keyPassword = "focusguard"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
