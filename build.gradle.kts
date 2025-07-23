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

    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.kotest:kotest-framework-engine:5.9.1")
    testImplementation("io.mockk:mockk:1.14.5")
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

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
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
