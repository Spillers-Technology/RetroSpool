plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.retrospool"
version = "0.0.2"

java {
    toolchain {
        // Production JDK is Eclipse Temurin 21 LTS (GPLv2 + Classpath Exception) — see docs/decisions.md D-002.
        // Vendor is intentionally not pinned here so the build runs on any conformant JDK 21
        // (CI/Docker uses Temurin); the runtime image in Dockerfile is Temurin.
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Spring Boot 3.4.1 manages Testcontainers 1.20.4, whose docker-java negotiates an API
// version Docker Engine 29+ rejects (400 on /info). Override the managed version.
extra["testcontainers.version"] = "1.21.3"

dependencies {
    // --- Web / persistence / ops (Apache-2.0) ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // --- Migrations + DB (Apache-2.0 / PostgreSQL License) ---
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // --- IBM i client (JTOpen / jt400 — IBM Public License) ---
    implementation("net.sf.jt400:jt400:21.0.6")

    // --- Capture/export libraries (wired in later phases; declared now to validate
    //     dependency resolution + licensing early — see docs/implementation-plan.md). ---
    implementation("org.apache.pdfbox:pdfbox:3.0.3")                       // Apache-2.0, text -> PDF
    implementation(platform("software.amazon.awssdk:bom:2.29.45"))         // Apache-2.0
    implementation("software.amazon.awssdk:s3")
    implementation("org.apache.sshd:sshd-sftp:2.14.0")                     // Apache-2.0 (MINA SSHD)
    implementation("commons-net:commons-net:3.11.1")                       // Apache-2.0 (FTPS)

    // --- Test ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Testcontainers modules are MIT; the Postgres/MinIO images they run are external
    // services over the network, never linked (D-002).
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:minio")
}

// Unit tests run everywhere; @Tag("integration") tests need a Docker socket
// (Testcontainers) and run via the separate integrationTest task — see
// docs/decisions.md D-017 for the docker-in-docker invocation.
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Testcontainers-backed tests (Postgres, MinIO, render sidecar). Needs a Docker socket."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("integration")
    }
    // Docker Engine 29+ rejects unversioned/legacy API requests from docker-java;
    // pin a versioned API path (1.44 = engine 29's supported floor).
    systemProperty("api.version", "1.44")
    shouldRunAfter(tasks.test)
}

// Licensing gate (D-002): no Ghostscript/AGPL/Oracle artifacts may ever reach the JVM
// runtime classpath. GhostPDL runs only in the render sidecar container (D-018); MinIO
// only as an external S3 service. Enforced on every `check`, not just prose.
val bannedDependencyKeywords = listOf(
    "ghostscript", "ghostpdl", "ghost4j", "itextpdf", "com.itextpdf",
    "io.minio", "com.oracle", "graalvm-enterprise"
)

val licenseGate = tasks.register("licenseGate") {
    group = "verification"
    description = "Fails the build if a banned (AGPL/Oracle) artifact is on the runtime classpath (D-002)."
    val runtimeClasspath = configurations.runtimeClasspath
    doLast {
        val violations = runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts
            .map { "${it.moduleVersion.id.group}:${it.moduleVersion.id.name}" }
            .filter { coordinate ->
                bannedDependencyKeywords.any { coordinate.contains(it, ignoreCase = true) }
            }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Banned dependencies on the runtime classpath (docs/decisions.md D-002): $violations"
            )
        }
    }
}

tasks.named("check") {
    dependsOn(licenseGate)
}
