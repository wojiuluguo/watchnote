plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    // 7G 内存机器上 lint 分析会吃光 metaspace 把构建搞崩；lint 只是静态检查，不影响产物，release 直接关掉
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    namespace = "com.ioo.watchnote"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ioo.watchnote"
        minSdk = 26
        targetSdk = 36
        versionCode = 136
        versionName = "4.1.27"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("watchDebug") {
            storeFile = file("debug.keystore")
            storePassword = "123456"
            keyAlias = "watchnote"
            keyPassword = "123456"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("watchDebug")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("watchDebug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation("androidx.compose.animation:animation")
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.material3)
    implementation(libs.coroutines.android)
}