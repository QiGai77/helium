plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Signing: CI secrets (HELIUM_KEYSTORE_*) win; otherwise the public, committed keystore in /keystore is used
// so every CI build has the same signature and can be installed / updated over the previous one.
val ksFile = System.getenv("HELIUM_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }?.let { file(it) }
    ?: rootProject.file("keystore/helium-public.jks")
val ksPassword = System.getenv("HELIUM_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() } ?: "helium-public"
val ksAlias = System.getenv("HELIUM_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "helium"
val ksKeyPassword = System.getenv("HELIUM_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: ksPassword

android {
    namespace = "tech.qigai.helium"
    compileSdk = 35

    defaultConfig {
        applicationId = "tech.qigai.helium"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("helium") {
            storeFile = ksFile
            storePassword = ksPassword
            keyAlias = ksAlias
            keyPassword = ksKeyPassword
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("helium")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        // Store the bundled variable fonts uncompressed so the platform can mmap them instead of
        // inflating 17 MB of Noto Sans SC into memory for every weight.
        noCompress += listOf("ttf")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // M5: scheduled task runner
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20240303")
}
