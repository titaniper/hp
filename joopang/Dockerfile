# syntax=docker/dockerfile:1.6

FROM gradle:8.6.0-jdk17 AS builder
WORKDIR /workspace
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY src ./src
COPY docs ./docs
COPY docker ./docker
COPY k6 ./k6
COPY README.md ./README.md
COPY .dockerignore ./
COPY gradlew ./gradlew
RUN chmod +x gradlew
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
ARG SPRING_PROFILE=stage
ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILE}
COPY --from=builder /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
