plugins {
    `java-library`
}

// Java 8 bytecode, so that any application can use the annotation.
tasks.withType<JavaCompile>().configureEach {
    options.release = 8
    options.encoding = "UTF-8"
}
