plugins {
    java
    id("org.springframework.boot") version "3.5.5"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "nl.ak.skillswap"
version = "0.0.1-SNAPSHOT"
description = "skillswap"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        // Spring Cloud release train compatible with Spring Boot 3.5.x
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.0")
    }
}

dependencies {
    // Gateway (WebFlux)
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")

    // Security + JWT resource server (Keycloak)
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // RabbitMQ for GDPR event orchestration
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    // WebClient for service calls
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Observability
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
