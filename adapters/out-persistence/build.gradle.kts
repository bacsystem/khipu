plugins { `java-library` }
dependencies {
    api(project(":application"))
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.jackson2.databind)
    implementation(libs.jackson2.jsr310)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgres)
    implementation(libs.postgres)   // la carga del padrón usa CopyManager (#361)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.testcontainers.junit)
}

// El docker-java empaquetado por Testcontainers 1.20.1 negocia por defecto una versión de la
// API de Docker anterior a la mínima que exponen los daemons recientes (MinAPIVersion >= 1.40),
// lo que hace fallar la detección del entorno Docker con 400 Bad Request. Se fija una versión
// de API soportada por cualquier Docker Engine moderno para que Testcontainers pueda conectarse.
tasks.test {
    systemProperty("api.version", "1.41")
}
