import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.0.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(providers.gradleProperty("javaVersion").get().toInt())
}

dependencies {
    intellijPlatform {
        create(
            providers.gradleProperty("platformType"),
            providers.gradleProperty("platformVersion"),
        )
        bundledPlugins(
            providers.gradleProperty("platformBundledPlugins")
                .map { it.split(',').map(String::trim).filter(String::isNotEmpty) },
        )

        instrumentationTools()
        pluginVerifier()
    }

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = providers.gradleProperty("javaVersion").get()
            freeCompilerArgs += listOf("-Xjvm-default=all")
        }
    }

    withType<JavaCompile> {
        sourceCompatibility = providers.gradleProperty("javaVersion").get()
        targetCompatibility = providers.gradleProperty("javaVersion").get()
    }

    test {
        useJUnitPlatform()
    }

    wrapper {
        gradleVersion = "8.10.2"
        distributionType = Wrapper.DistributionType.BIN
    }
}
