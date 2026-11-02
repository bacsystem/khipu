plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}
// El BOM de Spring Boot fija Testcontainers en 1.19.8; out-persistence (sin BOM) usa la del catálogo.
// Se alinean para que un classpath unido (p. ej. "todos los tests" en IntelliJ) no mezcle dos versiones de docker-java.
dependencies {
    implementation(project(":application"))
    implementation(project(":adapters:in-rest"))
    implementation(project(":adapters:in-scheduler"))
    implementation(project(":adapters:out-ubl"))
    implementation(project(":adapters:out-pdf"))
    implementation(project(":adapters:out-signing"))
    implementation(project(":adapters:out-sunat-soap"))
    implementation(project(":adapters:out-sunat-rest"))
    implementation(project(":adapters:out-storage"))
    implementation(project(":adapters:out-persistence"))
    implementation(project(":adapters:out-crypto"))
    implementation(project(":adapters:out-mail"))
    implementation(project(":adapters:out-alertas"))
    implementation(project(":adapters:out-webhook"))
    implementation(libs.springdoc.openapi)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.spring.boot.starter.webmvc)
    // DEPS-02: Boot 4 trae Jackson 3; la API sigue en Jackson 2 (ObjectMapper inyectado en los controladores, swagger-core de springdoc) hasta migrarla.
    implementation(libs.spring.boot.jackson2)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    runtimeOnly(libs.flyway.postgres)
    runtimeOnly(libs.postgres)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.resttestclient)
    testImplementation(libs.spring.boot.restclient)
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
    // DeveloperPortalE2ETest compara el OpenAPI de la API con el que el portal commitea (#5): sin declararlo, Gradle da la prueba por al día aunque ese archivo cambie.
    inputs.file("../portal/src/lib/api/openapi.json").withPropertyName("openApiDelPortal").withPathSensitivity(PathSensitivity.NONE)
    // TerminosVersionTest compara app.terminos-version con la versión de los textos legales del portal (#342): por lo mismo.
    inputs.file("../portal/src/content/legal/version.ts").withPropertyName("versionDeLosTerminosDelPortal").withPathSensitivity(PathSensitivity.NONE)
    // Cada E2E de Spring levanta su propio Postgres de Testcontainers, y con él su propio contexto, que Spring deja en caché durante toda la corrida: con
    // los 512 MB que Gradle da por defecto a un worker de pruebas, el decimosexto contexto agotaba el heap («OutOfMemoryError: Java heap space») y la
    // tarea fallaba sin ningún test en rojo.
    // Lo que más pesaba de cada contexto eran los cinco esquemas XSD UBL que compilaba su JaxpXsdValidator: ahora se compilan una vez por JVM, y con un
    // solo contexto por clase E2E (sin clases nuevas con contexto propio: las pruebas de una misma área van en la misma clase) la corrida completa llegó a
    // ~0,7 GB usados de este 1 GB. Cada contexto nuevo suma su propio heap: si vuelve a agotarse, mire primero qué retiene cada contexto
    // antes de subir esta cifra.
    maxHeapSize = "1g"
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
