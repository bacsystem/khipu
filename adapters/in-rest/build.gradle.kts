plugins { `java-library` }
dependencies {
    api(project(":application"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.tx)   // org.springframework.dao.DuplicateKeyException (mapeo a 409)
    testImplementation(libs.spring.boot.starter.test)
}
