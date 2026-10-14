plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "moldable-exceptions-intellij"

include(
    "moldable-views",          // @DebugView + DebugViews: what application code uses (optional dependency)
    "moldable-snapshot-rt",    // helper class loaded into the debugged JVM by the plugin
    "intellij-plugin",         // the IntelliJ plugin
    "samples:checkout-demo",   // demo application
)
