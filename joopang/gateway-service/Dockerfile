# syntax=docker/dockerfile:1.6

FROM gradle:8.6.0-jdk17 AS builder
WORKDIR /workspace

COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY gradlew ./gradlew
COPY common ./common
COPY order-service ./order-service
COPY coupon-service ./coupon-service
COPY gateway-service ./gateway-service

RUN chmod +x gradlew
RUN ./gradlew :gateway-service:bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
ARG SPRING_PROFILE=stage
ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILE}
COPY --from=builder /workspace/gateway-service/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
