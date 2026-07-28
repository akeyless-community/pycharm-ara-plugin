import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // Keep Gson inside the plugin distribution (lib/).
    implementation("com.google.code.gson:gson:2.11.0")

    intellijPlatform {
        // Build against 2025.2 (still broadly compatible); runtime tested on PyCharm 2026.2 (262.*).
        intellijIdeaCommunity("2025.2.5")
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
        zipSigner()
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.akeyless.ara"
        name = "Akeyless Agentic Runtime Authority"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "243"
            // Omit untilBuild so the plugin stays compatible with PY-262 and newer.
        }
        vendor {
            name = "Akeyless"
            email = "support@akeyless.io"
            url = "https://www.akeyless.io"
        }
    }

    signing {
        certificateChain.set(providers.environmentVariable("CERTIFICATE_CHAIN"))
        privateKey.set(providers.environmentVariable("PRIVATE_KEY"))
        password.set(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
    }

    publishing {
        token.set(providers.environmentVariable("PUBLISH_TOKEN"))
    }
}

tasks {
    withType<Test> {
        useJUnitPlatform()
    }
}
