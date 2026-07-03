# --- Build stage ---
FROM gradle:8.10.2-jdk21 AS build
WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .
RUN gradle --no-daemon clean bootJar -x test

# --- Runtime stage (Eclipse Temurin 21 — GPLv2 + Classpath Exception) ---
FROM eclipse-temurin:21-jre AS runtime
RUN useradd -r -u 1001 spool
USER 1001
WORKDIR /app
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
