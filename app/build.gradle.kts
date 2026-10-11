import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.baselineprofile)
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.paulneja.quorvi"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = providers.gradleProperty("quorvi.applicationId").get()
        minSdk = 29

        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val githubClientId = localProps.getProperty("github.clientId") ?: System.getenv("GITHUB_CLIENT_ID") ?: ""
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"$githubClientId\"")
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "LABS", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "LABS", "false")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    packaging {
        resources {
            excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/BCKEY.DSA", "META-INF/BCKEY.SF", "META-INF/DEPENDENCIES", "META-INF/LICENSE.md", "META-INF/NOTICE.md")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        disable += "ExpiredTargetSdkVersion"
        disable += "OldTargetApi"
    }
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { it.applicationId.set(providers.gradleProperty("quorvi.applicationIdDebug").get()) }
}

kotlin {
    jvmToolchain(21)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":runtime"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.miuix.blur)
    implementation(libs.okhttp)
    implementation(libs.sshj)
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":benchmark"))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.mina.sshd.core)
    testImplementation(libs.slf4j.nop)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
