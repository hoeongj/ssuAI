# syntax=docker/dockerfile:1.7
#
# Multi-stage build for the ssuAI Spring Boot backend.
#
# Build context: ./backend (the Spring Boot module).
# The image is multi-arch capable; CI builds linux/arm64 for the Oracle
# Cloud Ampere A1 production target via docker buildx.
#
#   docker build -f deploy/docker/Dockerfile -t ssuai-backend:dev ./backend
#   docker buildx build --platform linux/arm64 -f deploy/docker/Dockerfile \
#     -t ghcr.io/<owner>/ssuai-backend:<sha> --push ./backend

# ---- Build stage ----------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

# Cache Gradle wrapper + dependency resolution before copying sources so
# that source-only changes don't bust the dependency download layer.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --version

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# ---- Runtime stage --------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Run as non-root. curl is used by the HEALTHCHECK below.
RUN groupadd --system spring \
 && useradd --system --uid 1001 --gid spring --shell /usr/sbin/nologin spring \
 && apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

COPY --from=builder /workspace/build/libs/*-SNAPSHOT.jar app.jar
RUN chown spring:spring app.jar

USER 1001
EXPOSE 8080

# JVM defaults sized for a 1 GB pod request on the Ampere A1 host.
# Override with the JAVA_OPTS env var from the k8s ConfigMap when needed.
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
  CMD curl --fail --silent http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
