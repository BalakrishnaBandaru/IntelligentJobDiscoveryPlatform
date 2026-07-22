# =============================================================================
# Multi-stage build. The host needs NO JDK and NO Gradle — the build happens
# inside the JDK image using the project's own Gradle wrapper, and the app then
# runs on a slim JRE image.
# =============================================================================

# ---- Build stage ------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy the wrapper + build scripts first so the (slow) dependency-download
# layer is cached independently of source-code changes.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle ./

# Normalise line endings and make the wrapper executable. Guards against the
# script being checked out with Windows CRLF endings, which would otherwise
# break the shebang inside this Linux image.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Warm the dependency cache. "|| true" keeps this layer cacheable even when the
# resolve step has nothing further to do.
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Compile and package the executable (Spring Boot "fat") jar. Running bootJar
# specifically means only the runnable jar is produced (no -plain.jar), so the
# wildcard COPY in the runtime stage matches exactly one file.
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

# ---- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# curl lets the Docker/Compose healthcheck probe the Actuator endpoint.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as an unprivileged user instead of root.
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
