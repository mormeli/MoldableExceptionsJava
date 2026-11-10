plugins {
    id("java-library")
}

dependencies {
    // Packaging only: a program that depends on the runtime gets the annotations and DebugViews along with it.
    // The code of this module must NOT use these classes. It finds views by annotation name and reads results by
    // reflection, because the program and an injected runtime can each have their own copy of moldable-views, and the
    // same class from two class loaders is two different classes to the JVM. See README.md, "Why duck typing".
    api(project(":moldable-views"))

    // The tests declare views with parameters, which the runtime only knows from the manifest the processor writes.
    testAnnotationProcessor(project(":moldable-views-processor"))
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// The runtime reaches a program in two ways: as a dependency, or, if the program has none, the plugin defines these
// classes inside the DEBUGGED JVM from their bytes. IntelliJ refuses to load a helper compiled for a newer Java than
// the debuggee runs, so target Java 8. Only JDK dependencies: nothing else is there when it is injected.
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
