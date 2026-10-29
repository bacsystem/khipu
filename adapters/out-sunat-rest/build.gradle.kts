plugins { `java-library` }
// #318: las APIs REST de SUNAT (OAuth2 + JSON): la consulta integrada de validez, y más adelante la GRE (#39) y el SIRE (#322). Módulo aparte de out-sunat-soap
// porque no comparten protocolo ni formato, y los adaptadores no pueden depender entre sí. Jackson ya lo trae out-persistence (mismo starter, misma versión).
dependencies {
    api(project(":application"))
    implementation(libs.jackson2.databind)
    implementation(libs.jackson2.jsr310)
    testImplementation(libs.wiremock)
}
