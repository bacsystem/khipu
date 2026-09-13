plugins { `java-library` }
dependencies {
    api(project(":application"))
    implementation(libs.spring.context)
    implementation(libs.slf4j.api)
    testImplementation(libs.spring.boot.starter.test)
}
