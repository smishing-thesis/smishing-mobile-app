plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.app.smishing"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.app.smishing"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // APK de pruebas internas (#32): como el de producción (sin depuración), pero firmado con la clave de depuración para poder
        // instalarlo en un teléfono. No es para distribuir: eso es el App Bundle firmado de #24. Sin minificar, por ahora.
        create("qa") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            versionNameSuffix = "-qa"
            matchingFallbacks += "release"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Las pruebas de sharedTest corren igual en la JVM (test) y en el dispositivo (androidTest).
    // En la JVM, los assets de main (preprocessing_config.json) se leen como recursos.
    sourceSets {
        getByName("test") {
            kotlin.directories.add("src/sharedTest/java")
            resources.directories.add("src/sharedTest/assets")
            resources.directories.add("src/main/assets")
        }
        getByName("androidTest") {
            kotlin.directories.add("src/sharedTest/java")
            assets.directories.add("src/sharedTest/assets")
            // Los esquemas exportados de Room: la prueba de migración arma la base de la versión 1 con su SQL exacto.
            // (No se usa MigrationTestHelper: room-testing exige kotlinx-serialization 1.8 y la app resuelve la 1.7.3.)
            assets.directories.add("schemas")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.litert)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.datastore.preferences)
    ksp(libs.androidx.room.compiler)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    // En la JVM, org.json de android.jar es un stub; en el dispositivo se usa el real.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

// Room guarda el esquema de la base en un JSON versionado: es lo que permite escribir y probar migraciones.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
