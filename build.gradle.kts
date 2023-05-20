import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.7.0"
    id("application")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    java
}

group = "com.genomealmanac"
version = "2.0.1"

fun isRunningInGitHubActions(): Boolean {
    return System.getenv("GITHUB_ACTIONS")?.toBoolean() ?: false
}

repositories {
    mavenLocal()
    jcenter()
    if (isRunningInGitHubActions()) {
        maven {
            url = uri("https://maven.pkg.github.com/weng-lab/krews")
            credentials {
                username = "GitHub"
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    } else {
        maven {
            url = uri("https://maven.pkg.github.com/weng-lab/krews")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME") ?: ""
                password project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN") ?: ""
            }
        }
    }
}


dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.krews", "krews", "0.14.0")
    implementation("com.squareup.okhttp3", "okhttp", "3.12.1")
    implementation("com.squareup.moshi", "moshi-kotlin", "1.8.0")
    testImplementation("org.junit.jupiter", "junit-jupiter", "5.4.0")
    testImplementation("org.assertj", "assertj-core", "3.11.1")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("AppKt")
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("base")
    archiveClassifier.set("")
    destinationDirectory.set(file("build"))
}
