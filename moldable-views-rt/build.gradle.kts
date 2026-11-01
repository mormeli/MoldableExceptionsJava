plugins {
    id("java-library")
}

// The plugin defines these classes inside the DEBUGGED JVM from their bytes. IntelliJ refuses to load a helper
// compiled for a newer Java than the debuggee runs, so target Java 8. Only JDK dependencies: nothing else is there.
tasks.withType<JavaCompile>().configureEach {
    options.release = 8
    options.encoding = "UTF-8"
}

// META-INF/moldexj/runtime-classes: every class of this module, so the plugin knows what to define in the debuggee.
val runtimeClassList = tasks.register("runtimeClassList") {
    val classes = sourceSets.main.get().output.classesDirs
    val output = layout.buildDirectory.dir("generated/runtime-class-list")
    inputs.files(classes).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(output)
    doLast {
        val names = sortedSetOf<String>()
        classes.asFileTree.matching { include("**/*.class") }.visit {
            if (!isDirectory) {
                names.add(relativePath.pathString.removeSuffix(".class").replace('/', '.'))
            }
        }
        val file = output.get().file("META-INF/moldexj/runtime-classes").asFile
        file.parentFile.mkdirs()
        file.writeText(names.joinToString("\n", postfix = "\n"))
    }
}

tasks.jar {
    from(runtimeClassList)
}
