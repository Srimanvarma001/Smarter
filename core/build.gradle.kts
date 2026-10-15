import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Platform-independent logic (chess rules, puzzles, time budgets), unit-tested on the JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
    // The tests validate the puzzle pack that ships inside the Android app.
    systemProperty("puzzlePack", file("../app/src/main/assets/puzzles.csv").absolutePath)
}
