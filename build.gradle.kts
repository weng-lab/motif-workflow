import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.3.21"
    id("application")
    id("com.github.johnrengelman.shadow") version "4.0.2"
}

group = "com.genomealmanac"
version = "1.0.0"

repositories {
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    compile("io.krews", "krews", "0.8.3")
    implementation("com.squareup.okhttp3", "okhttp", "3.12.1")
    implementation("com.squareup.moshi", "moshi-kotlin", "1.8.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClassName = "AppKt"
}

val shadowJar: ShadowJar by tasks
shadowJar.apply {
    classifier = "exec"
    destinationDir = file("build")
}