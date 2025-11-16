plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Si usas el plugin Compose moderno vía version catalog, deja esta línea:
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
}

android {
    namespace = "com.d4vram.psychologger"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.d4vram.psychologger"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // --- FIRMA ---
    signingConfigs {
        create("release") {
            storeFile = file(project.findProperty("RELEASE_STORE_FILE") as String)
            storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as String
            keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String
            keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String
            // si tu AGP no soporta estos flags, elimínalos sin problema:
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        // Producción
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }

        // Desarrollo firmado con la misma clave (para que no moleste Play Protect)
        create("dev") {
            initWith(getByName("release"))
            isDebuggable = true
            signingConfig = signingConfigs.getByName("release")
            // Si quieres que conviva con la release, descomenta:
            // applicationIdSuffix = ".dev"
            // versionNameSuffix = "-dev"
        }

        // Debug “clásico” (si lo usas)
        getByName("debug") {
            isDebuggable = true
            // Si quieres que debug también vaya firmado estable, descomenta:
            // signingConfig = signingConfigs.getByName("release")
        }
    }

    // Java/Kotlin: usa 17 con AGP moderno
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Compose; si usas el plugin compose moderno, puedes mantener esto:
    buildFeatures {
        compose = true
    }
    composeOptions {
        // Ajusta a tu versión real de Compose Compiler si no usas el plugin nuevo
        kotlinCompilerExtensionVersion = "1.5.1"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // WebView
    implementation("androidx.webkit:webkit:1.8.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Biometric & Security
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)

    // Compose core
    implementation("androidx.compose.ui:ui:1.5.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.1")
    debugImplementation("androidx.compose.ui:ui-tooling:1.5.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.5.1")

    // Material (1.x) y Material3
    implementation("androidx.compose.material:material:1.5.1")
    implementation("androidx.compose.material3:material3:1.1.0")
    implementation("androidx.compose.material3:material3-window-size-class:1.1.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.navigation:navigation-fragment:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Icons
    implementation("androidx.compose.material:material-icons-extended:1.5.1")

    // JitPack libs
    implementation("com.github.prolificinteractive:material-calendarview:2.0.1") {
        exclude(group = "com.android.support", module = "support-compat")
    }
    implementation("com.github.PhilJay:MPAndroidChart:3.1.0") {
        exclude(group = "com.android.support")
    }

    // Lottie
    implementation("com.airbnb.android:lottie:6.2.0")

    // Core AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.8.0")

    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
