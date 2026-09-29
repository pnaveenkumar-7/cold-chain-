plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.coldchain.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.coldchain.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "CONTRACT_ADDRESS", "\"0xdeFE5922Dfd6af89571DfbD7c6559F01734E0C85\"")
        buildConfigField("String", "RPC_URL", "\"https://ethereum-sepolia-rpc.publicnode.com\"")
        buildConfigField("String", "BACKEND_URL", "\"https://frostledger-backend.onrender.com/\"")
        buildConfigField("String", "ETHERSCAN_TX_URL", "\"https://sepolia.etherscan.io/tx/\"")
        buildConfigField("String", "ETHERSCAN_ADDRESS_URL", "\"https://sepolia.etherscan.io/address/\"")
        buildConfigField("double", "TEMP_MIN", "2.0")
        buildConfigField("double", "TEMP_MAX", "8.0")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Retrofit + Moshi
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    ksp(libs.moshi.codegen)

    // DataStore
    implementation(libs.datastore.preferences)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // ML Kit Barcode
    implementation(libs.mlkit.barcode)

    // Accompanist Permissions
    implementation(libs.accompanist.permissions)

    // ZXing Core (QR generation)
    implementation(libs.zxing.core)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Unit Testing
    testImplementation(libs.junit)
}
