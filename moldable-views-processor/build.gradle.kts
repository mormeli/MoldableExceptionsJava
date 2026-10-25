plugins {
    id("java-library")
}

dependencies {
    // ViewManifest, the format shared with the plugin and the helper in the debugged JVM.
    implementation(project(":moldable-snapshot-rt"))
}

// Runs inside the javac of the application, which may be any JDK from 8 on.
tasks.withType<JavaCompile>().configureEach {
    options.release = 8
    options.encoding = "UTF-8"
}
