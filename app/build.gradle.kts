import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Lê local.properties de forma segura — nunca expõe segredos no código-fonte
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.bibliounifornew"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.bibliounifornew"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Injeta a chave como constante de build — acessível via BuildConfig.GOOGLE_BOOKS_API_KEY
        // O valor vem de local.properties e NUNCA é commitado.
        buildConfigField(
            "String",
            "GOOGLE_BOOKS_API_KEY",
            "\"${localProperties.getProperty("GOOGLE_BOOKS_API_KEY", "CHAVE_NAO_CONFIGURADA")}\""
        )
    }

    buildFeatures {
        buildConfig = true
    }

    // Assinatura de release lida de local.properties — nunca commitada.
    // Sem essas chaves configuradas, o build de release cai de volta para
    // a assinatura debug (útil em CI/checkout limpo, mas não deve ser usado
    // para publicar o APK oficial).
    val releaseStoreFile = localProperties.getProperty("RELEASE_STORE_FILE")
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (releaseStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    // Firebase BoM para alinhar as versões
    implementation(platform(libs.firebase.bom))

    // Firebase (versões gerenciadas pelo BoM)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation("com.google.firebase:firebase-storage")

    // Android UI e Core
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines para Firebase (.await)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Coil para carregamento de imagens por URL
    implementation("io.coil-kt:coil:2.7.0")

    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Retrofit para chamadas de internet
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0") // Converte o resultado para objetos Kotlin

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Testes
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
