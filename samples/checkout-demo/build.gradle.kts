plugins {
    application
}

dependencies {
    implementation(project(":moldable-views"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
}

tasks.withType<JavaExec>().configureEach {
    enableAssertions = true
}

application {
    mainClass = "demo.Main"
}
