plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // Bundled into the plugin; its class file is what gets injected into the debugged JVM.
    implementation(project(":moldable-snapshot-rt"))

    intellijPlatform {
        intellijIdea("2025.3")
        bundledPlugin("com.intellij.java")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

intellijPlatform {
    pluginConfiguration {
        version = "0.1.0"
        ideaVersion {
            sinceBuild = "253"
        }
    }
}
