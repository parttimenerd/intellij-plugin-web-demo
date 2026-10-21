import org.jetbrains.intellij.platform.gradle.TestFrameworkType

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.6.0"
}

group = properties("pluginGroup")
version = properties("pluginVersion")

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(properties("platformVersion"))
        bundledPlugin("com.intellij.java")
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }
    implementation("io.javalin:javalin:6.3.0")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginConfiguration {
        name = properties("pluginName")
        version = properties("pluginVersion")
        ideaVersion { sinceBuild = properties("pluginSinceBuild") }
    }
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
}

// ── Frontend build ────────────────────────────────────────────────────────────
val buildFrontend = tasks.register<Exec>("buildFrontend") {
    inputs.dir("frontend/src")
    inputs.files("frontend/package.json", "frontend/vite.config.ts")
    outputs.dir(layout.buildDirectory.dir("frontend-dist"))
    workingDir("frontend")
    commandLine("npm", "run", "build")
}

sourceSets.named("main") {
    resources.srcDir(layout.buildDirectory.dir("frontend-dist"))
}

tasks.named("processResources") { dependsOn(buildFrontend) }

tasks.test { useJUnitPlatform() }
