plugins { `java-library` }
dependencies {
    api(project(":application"))
    implementation(libs.spring.context)
    testImplementation(libs.spring.boot.starter.test)
}
