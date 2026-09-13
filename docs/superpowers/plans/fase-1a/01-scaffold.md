# 01 · Scaffold del proyecto

Índice del plan: [README](README.md). Leer primero **Global Constraints** allí.

### Task 1: Repositorio, Gradle multi-módulo y arranque de `bootstrap`

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `.gitignore`
- Create: `domain/build.gradle.kts`, `application/build.gradle.kts`, `bootstrap/build.gradle.kts`
- Create: `adapters/{in-rest,in-scheduler,out-ubl,out-signing,out-sunat-soap,out-storage,out-persistence,out-crypto}/build.gradle.kts`
- Create: `bootstrap/src/main/java/pe/factura/bootstrap/FacturaApplication.java`
- Create: `bootstrap/src/main/resources/application.yml`
- Test: `bootstrap/src/test/java/pe/factura/bootstrap/ArchitectureTest.java`

**Interfaces:**
- Produces: la estructura de módulos y el catálogo de versiones que todas las tareas siguientes usan (`libs.versions.toml`).

- [ ] **Step 1: Inicializar git y wrapper de Gradle**

```bash
cd /Users/christian/Documents/workspace/dev/clients/owner/claude/factura
git init -b main
gradle wrapper --gradle-version 8.10.2   # requiere gradle instalado (brew install gradle)
```

`.gitignore`:
```
.gradle/
build/
*/build/
adapters/*/build/
.idea/
*.iml
.DS_Store
storage/
.env
```

- [ ] **Step 2: Catálogo de versiones**

`gradle/libs.versions.toml`:
```toml
[versions]
springBoot = "3.3.4"
freemarker = "2.3.33"
testcontainers = "1.20.1"
wiremock = "3.9.1"
archunit = "1.3.0"
postgres = "42.7.4"
flyway = "10.17.3"

[libraries]
spring-boot-starter = { module = "org.springframework.boot:spring-boot-starter", version.ref = "springBoot" }
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web", version.ref = "springBoot" }
spring-boot-starter-validation = { module = "org.springframework.boot:spring-boot-starter-validation", version.ref = "springBoot" }
spring-boot-starter-jdbc = { module = "org.springframework.boot:spring-boot-starter-jdbc", version.ref = "springBoot" }
spring-boot-starter-actuator = { module = "org.springframework.boot:spring-boot-starter-actuator", version.ref = "springBoot" }
spring-boot-starter-test = { module = "org.springframework.boot:spring-boot-starter-test", version.ref = "springBoot" }
spring-boot-testcontainers = { module = "org.springframework.boot:spring-boot-testcontainers", version.ref = "springBoot" }
spring-tx = { module = "org.springframework:spring-tx", version = "6.1.13" }
spring-context = { module = "org.springframework:spring-context", version = "6.1.13" }
flyway-core = { module = "org.flywaydb:flyway-core", version.ref = "flyway" }
flyway-postgres = { module = "org.flywaydb:flyway-database-postgresql", version.ref = "flyway" }
postgres = { module = "org.postgresql:postgresql", version.ref = "postgres" }
freemarker = { module = "org.freemarker:freemarker", version.ref = "freemarker" }
testcontainers-postgres = { module = "org.testcontainers:postgresql", version.ref = "testcontainers" }
testcontainers-junit = { module = "org.testcontainers:junit-jupiter", version.ref = "testcontainers" }
wiremock = { module = "org.wiremock:wiremock-standalone", version.ref = "wiremock" }
archunit = { module = "com.tngtech.archunit:archunit-junit5", version.ref = "archunit" }
junit-bom = { module = "org.junit:junit-bom", version = "5.10.3" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter" }
assertj = { module = "org.assertj:assertj-core", version = "3.26.3" }
mockito = { module = "org.mockito:mockito-core", version = "5.12.0" }

[plugins]
spring-boot = { id = "org.springframework.boot", version.ref = "springBoot" }
spring-dependency-management = { id = "io.spring.dependency-management", version = "1.1.6" }
```

- [ ] **Step 3: Build raíz y settings**

`settings.gradle.kts`:
```kotlin
rootProject.name = "factura"
include(
    "domain", "application", "bootstrap",
    "adapters:in-rest", "adapters:in-scheduler",
    "adapters:out-ubl", "adapters:out-signing", "adapters:out-sunat-soap",
    "adapters:out-storage", "adapters:out-persistence", "adapters:out-crypto"
)
```

`build.gradle.kts`:
```kotlin
plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

allprojects {
    group = "pe.factura"
    version = "0.1.0"
    repositories { mavenCentral() }
}

subprojects {
    apply(plugin = "java")
    java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
    tasks.withType<JavaCompile> { options.encoding = "UTF-8"; options.compilerArgs.add("-parameters") }
    val catalog = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
    dependencies {
        "testImplementation"(platform(catalog.findLibrary("junit-bom").get()))
        "testImplementation"(catalog.findLibrary("junit-jupiter").get())
        "testImplementation"(catalog.findLibrary("assertj").get())
        "testImplementation"(catalog.findLibrary("mockito").get())
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
    tasks.withType<Test> { useJUnitPlatform(); testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL } }
}
```

- [ ] **Step 4: Builds de los módulos**

`domain/build.gradle.kts`:
```kotlin
plugins { `java-library` }
```

`application/build.gradle.kts`:
```kotlin
plugins { `java-library` }
dependencies { api(project(":domain")) }
```

`adapters/out-crypto/build.gradle.kts`, `adapters/out-storage/build.gradle.kts`, `adapters/out-signing/build.gradle.kts`:
```kotlin
plugins { `java-library` }
dependencies { api(project(":application")) }
```

`adapters/out-ubl/build.gradle.kts`:
```kotlin
plugins { `java-library` }
dependencies {
    api(project(":application"))
    implementation(libs.freemarker)
}
```

`adapters/out-sunat-soap/build.gradle.kts`:
```kotlin
plugins { `java-library` }
dependencies {
    api(project(":application"))
    testImplementation(libs.wiremock)
}
```

`adapters/out-persistence/build.gradle.kts`:
```kotlin
plugins { `java-library` }
dependencies {
    api(project(":application"))
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgres)
    runtimeOnly(libs.postgres)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.testcontainers.junit)
}
```

`adapters/in-rest/build.gradle.kts`:
```kotlin
plugins { `java-library` }
dependencies {
    api(project(":application"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    testImplementation(libs.spring.boot.starter.test)
}
```

`adapters/in-scheduler/build.gradle.kts`:
```kotlin
plugins { `java-library` }
dependencies {
    api(project(":application"))
    implementation(libs.spring.context)
    testImplementation(libs.spring.boot.starter.test)
}
```

`bootstrap/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}
dependencies {
    implementation(project(":application"))
    implementation(project(":adapters:in-rest"))
    implementation(project(":adapters:in-scheduler"))
    implementation(project(":adapters:out-ubl"))
    implementation(project(":adapters:out-signing"))
    implementation(project(":adapters:out-sunat-soap"))
    implementation(project(":adapters:out-storage"))
    implementation(project(":adapters:out-persistence"))
    implementation(project(":adapters:out-crypto"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgres)
    runtimeOnly(libs.postgres)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.wiremock)
    testImplementation(libs.archunit)
}
```

- [ ] **Step 5: Aplicación mínima**

`bootstrap/src/main/java/pe/factura/bootstrap/FacturaApplication.java`:
```java
package pe.factura.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "pe.factura")
@EnableScheduling
public class FacturaApplication {
    public static void main(String[] args) {
        SpringApplication.run(FacturaApplication.class, args);
    }
}
```

`bootstrap/src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: factura
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/factura}
    username: ${DB_USER:factura}
    password: ${DB_PASSWORD:factura}
  flyway:
    enabled: true
    locations: classpath:db/migration
  jackson:
    property-naming-strategy: SNAKE_CASE
    default-property-inclusion: non_null
server:
  port: 8080
management:
  endpoints:
    web:
      base-path: /
      exposure:
        include: health
app:
  mode: ${APP_MODE:multi}
  master-key: ${MASTER_KEY:}
  api-key-pepper: ${API_KEY_PEPPER:cambiar-en-produccion}
  platform-admin-key: ${PLATFORM_ADMIN_KEY:}
  storage:
    type: ${STORAGE_TYPE:fs}
    fs-root: ${STORAGE_FS_ROOT:./storage}
  sunat:
    timeout-seconds: ${SUNAT_TIMEOUT_SECONDS:15}
    beta-url: https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService
    prod-url: https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService
  outbox:
    intervalo-ms: ${OUTBOX_INTERVALO_MS:10000}
    max-intentos: ${OUTBOX_MAX_INTENTOS:20}
  zona-horaria: America/Lima
```

- [ ] **Step 6: Test de arquitectura (falla ahora porque no hay clases, pero compila)**

`bootstrap/src/test/java/pe/factura/bootstrap/ArchitectureTest.java`:
```java
package pe.factura.bootstrap;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "pe.factura", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule dominioNoDependeDeInfra = noClasses().that().resideInAPackage("pe.factura.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "pe.factura.application..", "pe.factura.adapters..", "pe.factura.bootstrap..",
                    "org.springframework..", "java.sql..", "javax.sql..", "freemarker..", "javax.xml.crypto..");

    @ArchTest
    static final ArchRule applicationNoDependeDeAdaptadores = noClasses().that().resideInAPackage("pe.factura.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "pe.factura.adapters..", "pe.factura.bootstrap..", "org.springframework..", "java.sql..", "freemarker..");

    @ArchTest
    static final ArchRule adaptadoresNoSeConocen = SlicesRuleDefinition.slices()
            .matching("pe.factura.adapters.(*)..").should().notDependOnEachOther()
            .allowEmptyShould(true);
}
```

Nota: la tercera regla usa la API de *slices* de ArchUnit (`SlicesRuleDefinition`): una clase en `pe.factura.adapters.rest..` no puede depender de `pe.factura.adapters.persistence..`. Cada adaptador usa su propio subpaquete: `pe.factura.adapters.rest`, `.scheduler`, `.ubl`, `.signing`, `.sunat`, `.storage`, `.persistence`, `.crypto`.

- [ ] **Step 7: Compilar y arrancar sin BD**

```bash
./gradlew build -x test
./gradlew :bootstrap:test --tests '*ArchitectureTest*'
```
Expected: `BUILD SUCCESSFUL` (las reglas pasan en vacío gracias a `allowEmptyShould`; el escaneo de `pe.factura` encuentra solo `FacturaApplication`).

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "chore: scaffold Gradle multi-módulo hexagonal con bootstrap Spring Boot y ArchUnit"
```
