import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.19.0"
}

rootProject.name = "moldable-exceptions-intellij"

include(
    "moldable-views",          // @DebugView + DebugViews: what application code uses to write views
    "moldable-views-processor", // annotation processor: writes META-INF/moldexj/views, the manifest of all views
    "moldable-views-rt",    // the runtime: brings moldable-views along; a dependency of the app, or injected by the plugin
    "intellij-plugin",         // the IntelliJ plugin
    "samples:demos",   // demo applications with moldable-views alone: the plugin injects the runtime
    "samples:console-demo", // a program with the runtime as a dependency: the plugin uses the program's copy
)

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}