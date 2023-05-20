import com.jfrog.bintray.gradle.BintrayExtension
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.kotlin.dsl.registering
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get

plugins {
    kotlin("jvm") version "1.7.0"
    id("application")
    id("com.jfrog.bintray") version "1.8.1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("maven-publish")
    java
}

group = "com.genomealmanac"
version = "2.2.0"

repositories {
    mavenLocal()
    jcenter()
    maven {
        url = uri("https://maven.pkg.github.com/weng-lab/krews")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME") ?: ""
            password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN") ?: ""
        }
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.krews", "krews", "0.14.1")
    implementation("com.squareup.okhttp3", "okhttp", "3.12.1")
    implementation("com.squareup.moshi", "moshi-kotlin", "1.8.0")
    implementation("io.projectreactor", "reactor-core", "3.4.11")
    implementation("io.github.microutils", "kotlin-logging", "1.6.10")
    testImplementation("org.junit.jupiter", "junit-jupiter", "5.4.0")
    testImplementation("org.assertj", "assertj-core", "3.11.1")
}

val sourcesJar = tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

val publicationName = "motif-workflow"
publishing {
    publications {
        create<MavenPublication>(publicationName) {
            from(components["java"])
            artifact(sourcesJar.get())
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/weng-lab/motif-workflow")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
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
