import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Claves de Supabase fuera del código: se ponen en local.properties (que no se
// sube al repo) y se inyectan como BuildConfig al compilar.
val supabaseProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.zyrovent.glauncher"
    compileSdk = 34
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "com.zyrovent.glauncher"
        minSdk = 24
        // Android 10 (API 29): W^X prohíbe ejecutar binarios del directorio de la
        // app, así que el JVM se lanza dentro del propio proceso (sin exec).
        targetSdk = 29
        versionCode = 2
        versionName = "1.1"

        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${supabaseProps.getProperty("SUPABASE_URL", "")}\""
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON",
            "\"${supabaseProps.getProperty("SUPABASE_ANON", "")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packaging {
        resources.excludes += setOf("META-INF/{AL2.0,LGPL2.1}")
        jniLibs {
            // Extraer los .so: libtagoff.so debe existir como fichero en disco
            // para poder usarlo en LD_PRELOAD del proceso del juego.
            useLegacyPackaging = true
        }
    }

    // Nativas: glauncher + tagoff (tagging de punteros) y pojavexec
    // (puente EGL/entrada de Pojav para el cliente).
    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/Android.mk")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // Descompresión XZ para los .tar.xz de los runtimes de OpenJDK para Android.
    implementation("org.tukaani:xz:1.9")

    // Coil para carga fluida de GIFs, avatares e imágenes en Compose
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-gif:2.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
