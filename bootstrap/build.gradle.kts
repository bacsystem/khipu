plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}
// El BOM de Spring Boot fija Testcontainers en 1.19.8; out-persistence (sin BOM) usa la del catálogo.
// Se alinean para que un classpath unido (p. ej. "todos los tests" en IntelliJ) no mezcle dos versiones de docker-java.
extra["testcontainers.version"] = libs.versions.testcontainers.get()
dependencies {
    implementation(project(":application"))
    implementation(project(":adapters:in-rest"))
    implementation(project(":adapters:in-scheduler"))
    implementation(project(":adapters:out-ubl"))
    implementation(project(":adapters:out-pdf"))
    implementation(project(":adapters:out-signing"))
    implementation(project(":adapters:out-sunat-soap"))
    implementation(project(":adapters:out-storage"))
    implementation(project(":adapters:out-persistence"))
    implementation(project(":adapters:out-crypto"))
    implementation(project(":adapters:out-mail"))
    implementation(libs.springdoc.openapi)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgres)
    runtimeOnly(libs.postgres)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.minio)
    testImplementation(libs.aws.s3)
    testImplementation(libs.wiremock)
    testImplementation(libs.archunit)
    // El SimpleClientHttpRequestFactory basado en HttpURLConnection lanza HttpRetryException
    // ("cannot retry due to server authentication, in streaming mode") cuando un POST con
    // cuerpo recibe un 401 en modo streaming. Con Apache HttpClient5 en el classpath de test,
    // Spring Boot construye el TestRestTemplate con HttpComponentsClientHttpRequestFactory,
    // que no tiene ese problema.
    testImplementation(libs.httpclient5)
}

// Ver adapters/out-persistence/build.gradle.kts: Testcontainers 1.20.1 no negocia con el
// Docker Engine local si no se fija esta versión de API.
tasks.withType<Test>().configureEach {
    systemProperty("api.version", "1.41")
}

tasks.test {
    useJUnitPlatform { excludeTags("homologacion") }
}

// Suite de homologación contra e-beta (#32): red + Docker; no forma parte de `test`. Evidencia en build/homologacion/.
tasks.register<Test>("homologacion") {
    description = "Emite los escenarios de factura contra e-beta de SUNAT y guarda XML/CDR como evidencia"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("homologacion") }
    val salida = layout.buildDirectory.dir("homologacion").get().asFile
    systemProperty("homologacion.salida", salida.absolutePath)
    doFirst { salida.deleteRecursively() }   // evidencia solo de esta ejecución
    outputs.upToDateWhen { false }
    testLogging { events("passed", "failed"); showStandardStreams = false; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
