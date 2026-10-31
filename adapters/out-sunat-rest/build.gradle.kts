plugins { `java-library` }
// #318: las APIs REST de SUNAT (OAuth2 + JSON): la consulta integrada de validez, y más adelante la GRE (#39) y el SIRE (#322). Módulo aparte de out-sunat-soap
// porque no comparten protocolo ni formato, y los adaptadores no pueden depender entre sí. Jackson ya lo trae out-persistence (mismo starter, misma versión).
dependencies {
    api(project(":application"))
    implementation(libs.jackson2.databind)
    implementation(libs.jackson2.jsr310)
    testImplementation(libs.wiremock)
}

// #318: la prueba controlada con SUNAT real (sale a Internet y, con credenciales, lee datos reales de una empresa): no forma parte de `test`.
// Se corre a propósito con `./gradlew :adapters:out-sunat-rest:validezReal` (variables VALIDEZ_*, ver deploy/README.md sección 13).
tasks.test {
    useJUnitPlatform { excludeTags("sunat-real") }
}

tasks.register<Test>("validezReal") {
    description = "Pregunta la validez de un comprobante a la API REST de SUNAT con las credenciales de una empresa (corrida controlada, #318)"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("sunat-real") }
    val salida = layout.buildDirectory.dir("validez-real").get().asFile
    systemProperty("validez.salida", salida.absolutePath)
    doFirst { salida.deleteRecursively() }   // evidencia solo de esta ejecución
    outputs.upToDateWhen { false }
    // El resumen ya lo imprime la prueba; sin esto Gradle lo esconde cuando la corrida cumple.
    testLogging { events("passed", "failed"); showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
