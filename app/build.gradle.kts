plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

// Configuración opcional de firma. Cuando los valores no están presentes
// Gradle cae automáticamente en el keystore debug para evitar errores.
val releaseStoreFilePath = project.findProperty("RELEASE_STORE_FILE") as? String
val releaseStorePassword = project.findProperty("RELEASE_STORE_PASSWORD") as? String
val releaseKeyAlias = project.findProperty("RELEASE_KEY_ALIAS") as? String
val releaseKeyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as? String

val releaseStoreFile = releaseStoreFilePath
    ?.takeIf { it.isNotBlank() }
    ?.let { file(it) }

val hasReleaseSigningConfig = releaseStoreFile != null &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

if (!hasReleaseSigningConfig) {
    logger.lifecycle("[PsychoLogger] Credenciales de firma ausentes. Usando keystore debug por defecto.")
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
    val releaseSigningConfig = if (hasReleaseSigningConfig) {
        signingConfigs.create("release") {
            storeFile = releaseStoreFile
            storePassword = releaseStorePassword!!
            keyAlias = releaseKeyAlias!!
            keyPassword = releaseKeyPassword!!
            // si tu AGP no soporta estos flags, elimínalos sin problema:
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    } else {
        null
    }

    buildTypes {
        // Producción
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = releaseSigningConfig ?: signingConfigs.getByName("debug")
        }

        // Desarrollo firmado con la misma clave (para que no moleste Play Protect)
        create("dev") {
            initWith(getByName("release"))
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = releaseSigningConfig ?: signingConfigs.getByName("debug")
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
        // Compose Compiler alineado con Kotlin 1.9.24 / Compose BOM 2024.09
        kotlinCompilerExtensionVersion = "1.5.14"
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

    // Compose core (usando BOM para mantener versiones alineadas)
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Material (1.x) y Material3
    implementation("androidx.compose.material:material")
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material3:material3-window-size-class")

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
    implementation("androidx.compose.material:material-icons-extended")

    // Annotations requeridas por bibliotecas de cifrado (Tink)
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

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
