plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.precisiontuner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.precisiontuner"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // ~/.android is read-only in this environment, so use a writable copy of
        // the debug keystore inside the workspace when it is present. On a normal
        // machine (writable ~/.android) this block leaves the default untouched.
        getByName("debug") {
            val localKeystore = rootProject.file("keystore/debug.keystore")
            if (localKeystore.exists()) {
                storeFile = localKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    val tinyCrepeEnabled = providers.gradleProperty("tinyCrepeEnabled")
        .map(String::toBoolean)
        .orElse(false)
    // Which CREPE capacity to bundle: tiny | small | medium | large | full.
    // Defaults to small (best measured on-device accuracy/speed tradeoff);
    // asset dir and model file follow "{capacity}Crepe".
    val crepeModel = providers.gradleProperty("crepeModel")
        .orElse("small")
    defaultConfig {
        buildConfigField("boolean", "TINY_CREPE_ENABLED", tinyCrepeEnabled.get().toString())
        buildConfigField("String", "CREPE_MODEL_ASSET", "\"${crepeModel.get()}_crepe_fp16.tflite\"")
        if (tinyCrepeEnabled.get()) {
            ndk { abiFilters += "arm64-v8a" }
        }
    }
    if (tinyCrepeEnabled.get()) {
        sourceSets.getByName("main").assets.srcDir("src/${crepeModel.get()}Crepe/assets")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
    compileOnly(libs.litert.api)
    testImplementation(libs.litert.api)
    if (providers.gradleProperty("tinyCrepeEnabled").map(String::toBoolean).orElse(false).get()) {
        runtimeOnly(libs.litert)
    }
}
