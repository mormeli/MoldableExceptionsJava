plugins {
    `java-library`
}

// The plugin defines this class inside the DEBUGGED JVM from its bytes. IntelliJ refuses to load a helper compiled
// for a newer Java than the debuggee runs, so target Java 8. No dependencies, no nested classes, no lambdas.
tasks.withType<JavaCompile>().configureEach {
    options.release = 8
    options.encoding = "UTF-8"
}
