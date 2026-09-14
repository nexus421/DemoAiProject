plugins {
    kotlin("jvm") version "2.4.10"
    application
}

group = "bayern.kickner"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "nexus421MavenReleases"
        url = uri("https://maven.kickner.bayern/releases")
    }
}

dependencies {
    implementation("bayern.kickner:Klogger:0.1.0")
    implementation("bayern.kickner:KotNexLib:4.4.1")

    testImplementation(kotlin("test"))
}

kotlin {
    // Amazon Corretto, current LTS - see README.md's "JDK distribution" note. Pinning the vendor
    // (not just the language version) means Gradle's toolchain resolution won't silently fall
    // back to a different distro if Corretto 25 isn't the one it finds/provisions.
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.AMAZON)
    }

    // Context parameters are still Experimental in Kotlin 2.4 - see README.md's "Context
    // parameters" section for what they are and when to reach for them.
    compilerOptions {
        freeCompilerArgs.add("-Xexplicit-context-arguments")
    }
}

application {
    mainClass.set("bayern.kickner.demo.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
