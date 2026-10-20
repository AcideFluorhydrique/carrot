// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.acidefluorhydrique.carrot"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.acidefluorhydrique.carrot"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.1.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        // CI 的測試版要能和 F-Droid 簽的正式版並存：不同包名才不會互相覆蓋、
        // 也不會因為簽章不同而裝不上。存檔各自獨立，測試不會弄髒正式版的進度。
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
}