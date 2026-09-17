import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// ---------------------------------------------------------------------------
// Build-time configuration (never hardcode credentials in source).
// Lookup order per key: .local-secrets.env → local.properties → environment.
// ONLY client-safe keys are baked into the app (see clientSafeKeys). Server
// secrets (HF token, Supabase access token, OneSignal REST key, webhook
// secret…) are used by tooling only and never reach the binary.
// See .env.example for the full list of supported names.
// ---------------------------------------------------------------------------
val clientSafeKeys = listOf(
    "SUPABASE_URL",
    "SUPABASE_ANON_KEY",
    "REVENUECAT_ANDROID_PUBLIC_SDK_KEY",
    "REVENUECAT_GALAXY_PUBLIC_SDK_KEY",
    "REVENUECAT_IOS_PUBLIC_SDK_KEY",
    "REVENUECAT_TEST_STORE_API_KEY",
    "REVENUECAT_ENTITLEMENT_ID",
    "REVENUECAT_OFFERING_ID",
    "ONESIGNAL_APP_ID",
    "AI_BACKEND",
    "CLOUD_AI_FALLBACK_ENABLED",
    "GEMMA_MODEL_FILE",
    "MODEL_DOWNLOAD_URL",
    "ADMOB_ANDROID_APP_ID",
    "ADMOB_NATIVE_COMMUNITY_AD_UNIT_ID",
    "PRIVACY_POLICY_URL",
    "TERMS_URL",
    "COMMUNITY_GUIDELINES_URL",
    "SUPPORT_URL",
)

val localSecretsEnv = Properties().apply {
    val f = rootProject.file(".local-secrets.env")
    if (f.exists()) f.inputStream().use { load(it) }
}
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(key: String): String =
    (localSecretsEnv.getProperty(key) ?: localProps.getProperty(key)
        ?: System.getenv(key) ?: "").trim()

val generateAppSecrets by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/secrets/kotlin")
    val values = clientSafeKeys.associateWith { secret(it) }
    inputs.property("values", values)
    outputs.dir(outDir)
    doLast {
        val dir = outDir.get().asFile.resolve("com/abbeysbite/app/core/config")
        dir.mkdirs()
        dir.resolve("AppSecrets.kt").writeText(buildString {
            appendLine("package com.abbeysbite.app.core.config")
            appendLine()
            appendLine("/** Generated at build time from local.properties / environment. Do not edit. */")
            appendLine("object AppSecrets {")
            values.forEach { (k, v) ->
                val name = k.split("_").joinToString("") { part ->
                    part.lowercase().replaceFirstChar { it.uppercase() }
                }.replaceFirstChar { it.lowercase() }
                appendLine("    const val $name: String = \"${v.replace("\"", "\\\"")}\"")
            }
            appendLine("}")
        })
    }
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.time.ExperimentalTime")
            }
        }
        commonMain {
            kotlin.srcDir(generateAppSecrets.map { it.outputs.files.singleFile })
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.material.icons.extended)
                implementation(libs.compose.ui)
                implementation(libs.compose.components.resources)
                implementation(libs.compose.ui.tooling.preview)

                implementation(libs.lifecycle.viewmodel.compose)
                implementation(libs.lifecycle.runtime.compose)
                implementation(libs.navigation.compose)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.logging)

                implementation(project.dependencies.platform(libs.supabase.bom))
                implementation(libs.supabase.auth)
                implementation(libs.supabase.postgrest)
                implementation(libs.supabase.storage)
                implementation(libs.supabase.realtime)
                implementation(libs.supabase.functions)

                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)

                implementation(libs.coil.compose)
                implementation(libs.coil.network.ktor)

                implementation(libs.multiplatform.settings)
                implementation(libs.multiplatform.settings.serialization)
                implementation(libs.multiplatform.settings.noarg)

                implementation(libs.revenuecat.core)
                implementation(libs.revenuecat.ui)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.compose.ui.tooling.preview)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.exifinterface)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.koin.android)
                implementation(libs.onesignal.android)
                // On-device Gemma (LiteRT-LM bundle) via MediaPipe LLM Inference
                implementation(libs.mediapipe.tasks.genai)
                implementation(libs.mediapipe.tasks.core)
                // Galaxy Store billing + AdMob revenue tracking — native
                // RevenueCat modules version-locked to the KMP-embedded SDK.
                // Both are on the classpath for both flavors; ACTIVATION is
                // decided at runtime by BuildConfig.STORE (exactly one store
                // configuration is ever initialized per process).
                implementation(libs.revenuecat.native)
                implementation(libs.revenuecat.store.galaxy)
                implementation(libs.revenuecat.admob)
                implementation(libs.play.services.ads)
            }
        }
        iosMain {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}

android {
    namespace = "com.abbeysbite.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    val configuredAdmobAppId = secret("ADMOB_ANDROID_APP_ID")
        .takeIf { it.isNotBlank() && it != "PENDING" }
    val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"

    defaultConfig {
        applicationId = "com.abbeysbite.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 2
        versionName = "1.1.0"
        // Debug uses Google's official TEST app id. Release is overwritten in
        // buildTypes below with a real id or an empty value, so production
        // builds never inherit test inventory accidentally.
        // Debug always uses Google's test application id. Release uses a
        // supplied production id or an empty value, which keeps AdMob safely
        // disabled until real serving configuration exists.
        manifestPlaceholders["admobAppId"] = testAdmobAppId
    }
    buildFeatures {
        buildConfig = true
    }

    // ------------------------------------------------------------------
    // Store distribution flavors. Same application id in both stores; the
    // flavor decides which RevenueCat store configuration is active.
    //   play   -> RevenueCat Google Play (goog_ key)
    //   galaxy -> RevenueCat Samsung Galaxy Store (galx_ key, native module)
    // ------------------------------------------------------------------
    flavorDimensions += "store"
    productFlavors {
        create("play") {
            dimension = "store"
            buildConfigField("String", "STORE", "\"play\"")
        }
        create("galaxy") {
            dimension = "store"
            buildConfigField("String", "STORE", "\"galaxy\"")
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // Release signing from signing/signing.properties (gitignored; see
    // SIGNING_HANDOFF.md). Absent file → unsigned release (CI-safe).
    val signingProps = Properties().apply {
        val f = rootProject.file("signing/signing.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val haveSigning = signingProps.getProperty("storeFile") != null
    if (haveSigning) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            manifestPlaceholders["admobAppId"] = testAdmobAppId
        }
        getByName("release") {
            manifestPlaceholders["admobAppId"] = configuredAdmobAppId ?: ""
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (haveSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    debugImplementation(libs.compose.ui.tooling)
}
