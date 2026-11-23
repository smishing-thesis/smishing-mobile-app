import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// La dirección del backend y su clave (#72, #82) no van en Git. Se leen en este orden, y un valor vacío no pisa a los siguientes:
//   1. la propiedad de Gradle `-Psmishing.backendUrl=...` / `-Psmishing.backendApiKey=...`,
//   2. la variable de entorno `SMISHING_BACKEND_URL` / `SMISHING_BACKEND_API_KEY` (así las pasa el workflow de GitHub desde sus secretos),
//   3. `local.properties`, con `smishing.backendUrl` y `smishing.backendApiKey`.
// Sin ellas, la app compila igual y no envía aportes (se quedan en la cola). Cuidado: una variable de entorno olvidada en el computador
// gana sobre `local.properties`.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun backendSetting(property: String, environmentVariable: String): String =
    listOf(
        project.findProperty(property) as String?,
        System.getenv(environmentVariable),
        localProperties.getProperty(property),
    ).firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) } ?: ""

// La clave de subida del App Bundle (#24) tampoco va en Git. Con ella se firma `bundleRelease`; Google Play vuelve a firmar con su propia clave
// (Play App Signing). Se lee de las variables de entorno `SMISHING_UPLOAD_STORE_FILE`, `SMISHING_UPLOAD_STORE_PASSWORD`,
// `SMISHING_UPLOAD_KEY_ALIAS` y `SMISHING_UPLOAD_KEY_PASSWORD` y, si no están, de `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`,
// `keyPassword`; la ruta del archivo es relativa a la raíz del proyecto o absoluta). Si falta alguna, el bundle sale **sin firmar**: compila
// igual (el CI no tiene la clave), pero Google Play no lo acepta.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun uploadKeySetting(property: String, environmentVariable: String): String? =
    listOf(System.getenv(environmentVariable), keystoreProperties.getProperty(property))
        .firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }

fun stringField(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.app.smishing"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.app.smishing"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BACKEND_URL", stringField(backendSetting("smishing.backendUrl", "SMISHING_BACKEND_URL")))
        buildConfigField("String", "BACKEND_API_KEY", stringField(backendSetting("smishing.backendApiKey", "SMISHING_BACKEND_API_KEY")))
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        val storeFile = uploadKeySetting("storeFile", "SMISHING_UPLOAD_STORE_FILE")
        val storePassword = uploadKeySetting("storePassword", "SMISHING_UPLOAD_STORE_PASSWORD")
        val keyAlias = uploadKeySetting("keyAlias", "SMISHING_UPLOAD_KEY_ALIAS")
        val keyPassword = uploadKeySetting("keyPassword", "SMISHING_UPLOAD_KEY_PASSWORD")
        if (storeFile != null && storePassword != null && keyAlias != null && keyPassword != null) {
            create("release") {
                this.storeFile = rootProject.file(storeFile)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        // Se declara antes que `qa`, que parte de él con `initWith`.
        release {
            // R8 (#24): recorta el código que no se usa, acorta los nombres y quita los recursos sin usar. Reduce el tamaño y dificulta leer el
            // código, pero no protege secretos: la clave del backend dentro del APK sigue pudiéndose extraer. `qa` parte de este tipo, así que
            // se prueba lo mismo que se publica. El `mapping.txt` de cada compilación sirve para leer los errores del release.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sin la clave de subida (`signingConfigs` sin "release"), queda sin firmar.
            signingConfig = signingConfigs.findByName("release")
        }
        // APK de pruebas internas (#32): como el de producción (sin depuración), pero firmado con la clave de depuración para poder
        // instalarlo en un teléfono. No es para distribuir: eso es el App Bundle firmado de #24.
        create("qa") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            versionNameSuffix = "-qa"
            matchingFallbacks += "release"
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
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.material)
    implementation(libs.litert)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
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
