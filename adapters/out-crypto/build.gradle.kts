plugins { `java-library` }
dependencies {
    api(project(":application"))
    implementation(libs.spring.security.crypto)
    implementation(libs.java.jwt)
}
