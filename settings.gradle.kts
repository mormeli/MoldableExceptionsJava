import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.19.0"
}

rootProject.name = "moldable-exceptions-intellij"

include(
    "moldable-views",          // @DebugView + DebugViews: what application code uses (optional dependency)
    "moldable-views-processor", // annotation processor: writes META-INF/moldexj/views, the manifest of all views
    "moldable-views-rt",    // helper class loaded into the debugged JVM by the plugin
    "intellij-plugin",         // the IntelliJ plugin
    "samples:demos",   // demo application
)

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}