plugins { `java-library` }
dependencies {
    api(project(":application"))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.jackson2)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.tx)   // org.springframework.dao.DuplicateKeyException (mapeo a 409)
    implementation(libs.swagger.annotations)   // @Schema(example=...) en los DTOs, leído por springdoc en bootstrap
    implementation(libs.fastexcel)   // #321: exportación de comprobantes a XLSX, escrita en streaming
    testImplementation(libs.spring.boot.starter.webmvc.test)
}
// CasosDeLaGuiaTest valida los ejemplos de la guía del portal contra la API: sin declararlo, un cambio solo en la guía no volvería a correr los tests.
tasks.test { inputs.file(rootProject.file("portal/src/components/developers/guia/casos-emision.ts")) }
