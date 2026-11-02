plugins { `java-library` }
dependencies {
    api(project(":application"))
    implementation(libs.slf4j.api)
    testImplementation(libs.wiremock)
}
