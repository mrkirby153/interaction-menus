import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    kotlin("jvm")
}

repositories {
    mavenCentral()
    maven(url = "https://m2.dv8tion.net/releases")
    maven(url = "https://repo.mrkirby153.com/repository/maven-public/")
}


group = "com.mrkirby153"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0-RC")
    implementation("net.dv8tion:JDA:5.3.0")
    implementation("ch.qos.logback:logback-classic:1.4.6")
    implementation("com.mrkirby153:bot-core:8.0-SNAPSHOT")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
}
tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xcontext-receivers")
    }
}
kotlin {
    jvmToolchain(17)
}