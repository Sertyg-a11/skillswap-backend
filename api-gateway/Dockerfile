# ---- Build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew gradlew
COPY gradle gradle
RUN chmod +x gradlew
COPY build.gradle.kts settings.gradle.kts ./
RUN ./gradlew --no-daemon dependencies || true
COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- Run stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
USER app
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
