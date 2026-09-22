# Build context: raíz del repo (proyecto Gradle multi-módulo). En Railway: Root Directory = ".",
# Dockerfile Path = "deploy/backend/Dockerfile".
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY . .
RUN ./gradlew :bootstrap:bootJar --no-daemon -x test

# -jammy en vez de -alpine: la app firma XML con PKCS#12/JCE y se evita musl libc en certificados/locale.
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd -r khipu && useradd -r -g khipu khipu
COPY --from=build --chown=khipu:khipu /app/bootstrap/build/libs/*.jar app.jar
USER khipu
EXPOSE 8001
ENTRYPOINT ["java", "-jar", "app.jar"]
