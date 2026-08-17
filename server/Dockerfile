# syntax=docker/dockerfile:1

# ---- build ----
FROM eclipse-temurin:21-jdk AS build

WORKDIR /build

# Dependencies resolve from the POM alone, so copying it first keeps that layer cached across
# source changes. go-offline is only an optimisation: anything it misses is fetched by the
# package step below.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B --no-transfer-progress dependency:go-offline

COPY src/ src/
RUN ./mvnw -B --no-transfer-progress clean package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:21-jre

# curl is here for the container healthcheck, which probes the actuator endpoint
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# An unprivileged user: nothing this process does needs root
RUN useradd --system --create-home --uid 1001 kumusha
USER kumusha

WORKDIR /app
COPY --from=build --chown=kumusha:kumusha /build/target/*.jar app.jar

EXPOSE 3001

# Readiness rather than plain health: it reports DOWN while MongoDB is unreachable, so an
# instance that is running but cannot serve requests is not sent traffic
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -fsS http://localhost:3001/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
