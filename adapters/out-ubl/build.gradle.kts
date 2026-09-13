plugins { `java-library` }
dependencies {
    api(project(":application"))
    implementation(libs.freemarker)
}
