plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)  // @Serializable for type-safe Navigation routes
    id("com.google.dagger.hilt.android")      // Hilt plugin
    alias(libs.plugins.ksp)                   // Kotlin Symbol Processing(KSP) — for Room & Hilt
}

android {
    namespace = "com.lakmalz.spendwise"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.lakmalz.spendwise"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {

    // ── Compose BOM ─────────────────────────────────────────────────────────
    // BOM = Bill of Materials. It pins ALL Compose library versions together
    // so you never get version mismatch errors between compose-ui and material3
    val composeBom = platform(libs.androidx.compose.bom)   // version in libs.versions.toml
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended") // for Icons.Default.*

    // ── Core Android ─────────────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")

    // ── Splash Screen ────────────────────────────────────────────────────────
    implementation("androidx.core:core-splashscreen:1.2.0")

    // ── Lifecycle + ViewModel ────────────────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    // collectAsStateWithLifecycle() lives here — critical for safe Flow collection
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")

    // ── Navigation (type-safe routes with @Serializable — Navigation 2.8+) ──
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // ── Paging 3 ─────────────────────────────────────────────────────────────
    // Loads expenses in pages — avoids loading entire DB table into memory
    implementation("androidx.paging:paging-runtime:3.3.2")
    implementation("androidx.paging:paging-compose:3.3.2")

    // ── Hilt ────────────────────────────────────────────────────────────────
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-compiler:2.59.2")           // ksp not kapt — faster
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0") // hiltViewModel()
    implementation("androidx.hilt:hilt-work:1.2.0")         // @HiltWorker support
    ksp("androidx.hilt:hilt-compiler:1.2.0")                // Hilt AndroidX compiler

    // ── WorkManager ──────────────────────────────────────────────────────────
    // Budget alerts and periodic background tasks
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // ── Room ─────────────────────────────────────────────────────────────────
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")          // Flow support for DAOs
    ksp("androidx.room:room-compiler:2.8.4")

    // ── Retrofit + Networking ────────────────────────────────────────────────
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0") // logs API calls

    // ── DataStore ────────────────────────────────────────────────────────────
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ── Coroutines ───────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ── Testing ──────────────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.11")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")   // Compose Preview
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}