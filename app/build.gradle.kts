import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.smarter.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.smarter.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Robolectric renders the real screens on the JVM (see ScreenshotTest).
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// ScreenshotTest writes its PNGs here. Declaring the folder as an output means a test run
// restored from the build cache restores the screenshots too, instead of leaving none.
tasks.withType<Test>().configureEach {
    outputs.dir(layout.buildDirectory.dir("screenshots"))
}

dependencies {
    implementation(project(":core"))

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
