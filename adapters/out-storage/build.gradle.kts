plugins { `java-library` }
dependencies {
    api(project(":application"))
    implementation(libs.aws.s3)
    runtimeOnly(libs.aws.url.connection.client)
    implementation(libs.slf4j.api)
    testImplementation(libs.testcontainers.minio)
    testImplementation(libs.testcontainers.junit)
}

// Misma razón que en out-persistence: el docker-java de Testcontainers 1.20.1 negocia una API de Docker demasiado antigua.
tasks.test {
    systemProperty("api.version", "1.41")
}
