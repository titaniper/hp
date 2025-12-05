# syntax=docker/dockerfile:1.6
# Docker BuildKit의 최신 기능을 사용하기 위한 문법 버전 지정입니다.

# -----------------------------------------------------------------------------
# 1. Build Stage (빌드 단계)
# 애플리케이션을 빌드하기 위한 환경입니다. JDK와 Gradle이 포함된 이미지를 사용합니다.
# 'AS builder'는 이 단계를 'builder'라는 이름으로 참조하여 나중에 결과물만 가져오기 위함입니다.
# -----------------------------------------------------------------------------
FROM gradle:8.6.0-jdk17 AS builder

# 작업 디렉토리를 /workspace로 설정합니다. 이후 명령어들은 이 위치에서 실행됩니다.
WORKDIR /workspace

# 빌드에 필요한 설정 파일들을 복사합니다.
# 캐시 효율을 위해 변경이 적은 파일들을 먼저 복사하는 것이 좋습니다.
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle

# 소스 코드와 기타 필요한 리소스들을 복사합니다.
COPY src ./src
COPY docs ./docs
COPY docker ./docker
COPY k6 ./k6
COPY README.md ./README.md
COPY .dockerignore ./
COPY gradlew ./gradlew

# gradlew 스크립트에 실행 권한을 부여합니다.
RUN chmod +x gradlew

# Gradle을 사용하여 Spring Boot 애플리케이션을 빌드합니다 (bootJar).
# --no-daemon: CI/CD나 Docker 환경에서는 데몬을 띄우지 않고 1회성으로 실행하여 메모리를 아낍니다.
RUN ./gradlew bootJar --no-daemon

# -----------------------------------------------------------------------------
# 2. Runtime Stage (실행 단계)
# 실제 애플리케이션을 실행할 가볍고 최적화된 환경입니다.
# JDK 대신 실행에 필요한 JRE만 포함된 경량 이미지를 사용하여 이미지 크기를 줄입니다.
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre

# 실행 환경의 작업 디렉토리를 /app으로 설정합니다.
WORKDIR /app

# 빌드 시점에 넘겨받을 수 있는 변수입니다 (예: docker build --build-arg SPRING_PROFILE=prod ...).
# 기본값은 'stage'로 설정되어 있습니다.
ARG SPRING_PROFILE=stage

# 컨테이너 실행 시 적용될 환경 변수입니다.
# 위에서 받은 ARG 값을 환경 변수로 설정하여 Spring Boot가 인식하게 합니다.
ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILE}

# 'builder' 단계에서 생성된 JAR 파일(빌드 결과물)만 복사해옵니다.
# 소스 코드나 빌드 도구 없이 실행 파일만 가져오므로 보안과 용량 면에서 유리합니다.
COPY --from=builder /workspace/build/libs/*.jar app.jar

# 컨테이너가 8080 포트를 사용한다는 것을 명시합니다 (문서화 목적).
# 실제 포트 개방은 docker run -p 옵션으로 해야 합니다.
EXPOSE 8080

# 컨테이너가 시작될 때 실행할 명령입니다.
# java -jar /app/app.jar 명령어로 애플리케이션을 띄웁니다.
ENTRYPOINT ["java","-jar","/app/app.jar"]
