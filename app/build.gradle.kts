plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.decmike.gestion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.decmike.gestion"
        minSdk = 28
        targetSdk = 35
        // Numéro de build GitHub Actions : chaque version s'installe par-dessus la précédente.
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = run
        versionName = "1.0.$run"
    }

    // Clé de signature fixe, fournie hors du dépôt :
    //  - GitHub Actions : secret SIGNING_KEY (décodé par le workflow) ;
    //  - en local : gestionKeystore=/chemin/gestion-release.jks et gestionSigningPassword=… dans ~/.gradle/gradle.properties.
    val keystorePath = System.getenv("SIGNING_KEYSTORE") ?: (project.findProperty("gestionKeystore") as String?)
    val signingPassword = System.getenv("SIGNING_PASSWORD") ?: (project.findProperty("gestionSigningPassword") as String?)
    val fixedKey = !keystorePath.isNullOrBlank() && !signingPassword.isNullOrBlank() && file(keystorePath).exists()
    signingConfigs {
        if (fixedKey) create("fixed") {
            storeFile = file(keystorePath!!)
            storePassword = signingPassword
            keyAlias = "gestion"
            keyPassword = signingPassword
        }
    }

    buildTypes {
        debug {
            if (fixedKey) signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (fixedKey) signingConfigs.getByName("fixed") else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.documentfile:documentfile:1.0.1")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("com.google.guava:guava:33.3.1-android")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
