plugins { `java-library` }
dependencies {
    api(project(":application"))
    implementation(libs.freemarker)
    implementation(libs.flying.saucer.pdf)
    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)
    implementation(libs.slf4j.api)
}
