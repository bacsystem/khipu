# syntax=docker/dockerfile:1
# Build context: raíz del repo (proyecto Gradle multi-módulo). En Railway: Root Directory = ".",
# Dockerfile Path = "deploy/backend/Dockerfile". Requiere BuildKit por el cache mount de abajo.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY . .
# El caché de Gradle va en un cache mount y no en la capa del COPY. Donde el constructor conserve ese
# mount entre builds (Docker local; en Railway depende de si reusa el builder), las dependencias ya
# descargadas se reutilizan; donde no, el build es igual de correcto, solo vuelve a bajarlas.
RUN --mount=type=cache,target=/root/.gradle/caches \
    ./gradlew :bootstrap:bootJar --no-daemon -x test

# -jammy en vez de -alpine: la app firma XML con PKCS#12/JCE y se evita musl libc en certificados/locale.
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd -r khipu && useradd -r -g khipu khipu
COPY --from=build --chown=khipu:khipu /app/bootstrap/build/libs/*.jar app.jar
USER khipu
# El puerto real lo fija PORT (Railway lo inyecta); 8001 es solo el default de application.yml.
EXPOSE 8001
ENTRYPOINT ["java", "-jar", "app.jar"]
