plugins { `java-library` }
dependencies {
    api(project(":application"))
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.slf4j.api)
}
