plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

dependencies {
    // Bundled into the plugin: its classes are defined in the debugged JVM, and ViewManifest reads library manifests.
    implementation(project(":moldable-views-rt"))
    implementation("de.redsix:pdfcompare:1.2.11")

    intellijPlatform {
        intellijIdea("2025.3")
        bundledPlugin("com.intellij.java")
    }

    // Plain unit tests, for code that needs no running IDE (the log reader).
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // The IntelliJ Platform's JUnit 5 session listener refers to JUnit 4 classes; without them no test starts.
    testRuntimeOnly("junit:junit:4.13.2")
}

tasks.test {
    useJUnitPlatform()
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
