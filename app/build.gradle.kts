plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Номер сборки приходит из GitHub Actions; локально — 1.
val buildNumber = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1

// Постоянный ключ подписи: без него обновление не встанет поверх старой версии.
val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
val keystorePassword = System.getenv("SIGNING_PASSWORD")

android {
    namespace = "com.adskipper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.adskipper"
        minSdk = 24
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        if (keystorePath != null && keystorePassword != null) {
            create("release") {
                storeFile = file(keystorePath)
                storeType = "pkcs12"
                storePassword = keystorePassword
                keyAlias = "adskipper"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
