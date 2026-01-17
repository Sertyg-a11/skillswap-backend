# syntax=docker/dockerfile:1

FROM gradle:8.10.2-jdk21 AS build
WORKDIR /workspace

# Copy only build files first for dependency caching
COPY settings.gradle.kts build.gradle.kts gradle.properties* /workspace/
COPY gradle /workspace/gradle
COPY api-gateway/build.gradle.kts /workspace/api-gateway/build.gradle.kts

# Warm up dependency cache (no tests)
RUN gradle :api-gateway:dependencies --no-daemon

# Copy source
COPY api-gateway /workspace/api-gateway

# Build bootJar
RUN gradle :api-gateway:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Create non-root user
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /workspace/api-gateway/build/libs/*.jar /app/app.jar

EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
