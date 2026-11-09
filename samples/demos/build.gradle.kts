plugins {
    id("application")
}

repositories {
    mavenCentral()
}

dependencies {
    // moldable-views alone, without the runtime: the plugin injects the runtime when a demo is debugged.
    // samples/console-demo has the runtime as a dependency instead.
    implementation(project(":moldable-views"))
    annotationProcessor(project(":moldable-views-processor"))
    implementation("org.hibernate.validator:hibernate-validator:9.1.5.Final")
    implementation("org.glassfish:jakarta.el:4.0.2")
    implementation("org.mockito:mockito-core:5.+")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

tasks.withType<JavaExec>().configureEach {
    enableAssertions = true
}

application {
    mainClass = "demo.Main"
}
