plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

dependencies {
    // Bundled into the plugin: its classes are defined in the debugged JVM, and ViewManifest reads library manifests.
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
