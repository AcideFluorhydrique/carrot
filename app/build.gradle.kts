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

    signingConfigs {
        // 固定的 debug 金鑰。CI 每次都在全新的機器上建置，沒有這個的話每次都會產生一把新的隨機金鑰，
        // 新版因為簽章不同而無法覆蓋安裝，只能先解除安裝、連存檔一起丟掉。
        // debug 金鑰不是秘密（Android SDK 預設的那把密碼一樣是公開的 android），放在 repo 裡沒有問題；
        // F-Droid 的原始碼掃描器也不會標記 .p12。release 版不受影響，那是 F-Droid 自己簽的。
        getByName("debug") {
            storeFile = file("debug.p12")
            storeType = "PKCS12"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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