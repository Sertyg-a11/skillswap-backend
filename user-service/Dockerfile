# syntax=docker/dockerfile:1

FROM gradle:8.10.2-jdk21 AS build
WORKDIR /workspace

# Copy only build files first for dependency caching
COPY settings.gradle.kts build.gradle.kts gradle.properties* /workspace/
COPY gradle /workspace/gradle
COPY user-service/build.gradle.kts /workspace/user-service/build.gradle.kts

# Warm up dependency cache (no tests)
RUN gradle :user-service:dependencies --no-daemon

# Copy source
COPY user-service /workspace/user-service

# Build bootJar
RUN gradle :user-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /workspace/user-service/build/libs/*.jar /app/app.jar

EXPOSE 8082
ENTRYPOINT ["java","-jar","/app/app.jar"]
