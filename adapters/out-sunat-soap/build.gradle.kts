plugins { `java-library` }
dependencies {
    api(project(":application"))
    testImplementation(libs.wiremock)
}
