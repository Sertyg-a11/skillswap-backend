plugins {
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.springframework.boot") version "3.5.5" apply false
    id("org.owasp.dependencycheck") version "12.1.0"
    java
}

allprojects {
    group = "nl.ak.skillswap"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.owasp.dependencycheck")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        testImplementation("org.springframework.boot:spring-boot-starter-test")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

// OWASP Dependency Check configuration
dependencyCheck {
    formats = listOf("HTML", "JSON", "SARIF")
    outputDirectory = layout.buildDirectory.dir("reports/dependency-check").get().asFile.absolutePath
    suppressionFile = "dependency-check-suppressions.xml"
    failBuildOnCVSS = 9.0f // Only fail on critical vulnerabilities
    analyzers {
        assemblyEnabled = false
        nodeEnabled = false
    }
}
