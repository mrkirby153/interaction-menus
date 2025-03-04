import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("maven-publish")
    id("org.jetbrains.dokka") version "1.9.20"
    kotlin("jvm") version "2.1.10"
}

repositories {
    mavenCentral()
    maven("https://repo.mrkirby153.com/repository/maven-public/")
}

group = "com.mrkirby153"

dependencies {
    implementation("org.apache.logging.log4j:log4j-core:2.19.0")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    compileOnly("net.dv8tion:JDA:5.3.0")

    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.0.7")
}

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
    withJavadocJar()
}

fun publishUrl() = if (project.version.toString().endsWith("-SNAPSHOT")) {
    "https://repo.mrkirby153.com/repository/maven-snapshots/"
} else {
    "https://repo.mrkirby153.com/repository/maven-releases"
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "mrkirby153"
            url = uri(publishUrl())
            credentials {
                username = System.getenv("REPO_USERNAME")
                password = System.getenv("REPO_PASSWORD")
            }
        }
    }
}

tasks {
    withType<DokkaTask>().configureEach {
        suppressInheritedMembers.set(true)
    }
    withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    withType<KotlinCompile> {
        kotlinOptions {
            javaParameters = true
            // Experimental context receiver support
            freeCompilerArgs += "-Xcontext-receivers"
        }
    }
}