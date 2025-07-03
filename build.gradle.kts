import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.20"
    application
}

group = "com.risto"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // JInput for USB gamepad support
    implementation("net.java.jinput:jinput:2.0.9")

    // Kotlin stdlib (explicit for clarity)
    implementation(kotlin("stdlib"))
}

application {
    mainClass.set("nestor.MainKt")
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

// Optional: point to native libs if needed for JInput
val osName = System.getProperty("os.name").lowercase()
val nativeDir = when {
    osName.contains("win") -> "native/windows"
    osName.contains("mac") -> "native/macosx"
    else -> "native/linux"
}

tasks.withType<JavaExec> {
    jvmArgs = listOf("-Djava.library.path=$projectDir/libs/$nativeDir")
}
