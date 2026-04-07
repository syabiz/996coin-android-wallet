import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.navigation.safeargs)
}

extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    namespace = "com.coin996.wallet"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.coin996.wallet"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "COIN_NAME",      "\"996coin\"")
        buildConfigField("String", "COIN_TICKER",    "\"NNS\"")
        buildConfigField("String", "BECH32_HRP",     "\"996\"")
        buildConfigField("int",    "P2P_PORT",       "49969")
        buildConfigField("int",    "RPC_PORT",       "48931")
        buildConfigField("int",    "P2PKH_PREFIX",   "53")
        buildConfigField("int",    "P2SH_PREFIX",    "18")
        buildConfigField("int",    "WIF_PREFIX",     "128")
        buildConfigField("String", "PRICE_API_BASE", "\"https://klingex.io/api/v1/\"")
        buildConfigField("String", "PRICE_PAIR",     "\"NNS-USDT\"")
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "DEBUG_SPV", "false")
        }
        debug {
            isDebuggable        = true
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
            buildConfigField("boolean", "DEBUG_SPV", "true")
        }
    }

    compileOptions {
        sourceCompatibility            = JavaVersion.VERSION_17
        targetCompatibility            = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "*.proto"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring(libs.android.desugarJdkLibs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.runtime)

    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.workmanager)
    ksp(libs.hilt.workmanager.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    ksp(libs.moshi.codegen)

    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)

    implementation(libs.coil)
    implementation(libs.zxing.core)
    implementation(libs.zxing.embedded)
    implementation(libs.bitcoinj.core)
    implementation(libs.security.crypto)
    implementation(libs.biometric)
    implementation(libs.viewpager2)
    implementation(libs.swiperefresh)
    implementation(libs.lottie)
    implementation(libs.mpandroidchart)
    implementation(libs.datastore.preferences)
    implementation(libs.workmanager)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
