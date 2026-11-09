plugins {
    id("application")
}

dependencies {
    // The runtime as a dependency (it brings moldable-views along): the plugin uses this copy instead of injecting its
    // own. samples/demos depends on moldable-views alone and tests the injection.
    implementation(project(":moldable-views-rt"))
    annotationProcessor(project(":moldable-views-processor"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

application {
    mainClass = "ch.moldexj.consoledemo.OrderRejectedDemo"
}
