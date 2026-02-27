# Build stage
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies first to leverage Docker cache
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
ARG RUN_AS_USER=1741
ARG RUN_AS_GROUP=1030
ENV USER=indexer
ENV UID=$RUN_AS_USER
ENV GID=$RUN_AS_GROUP

# RIBS like: Create exact UID/GID user (NFS)
RUN apk add --no-cache shadow && \
    addgroup --gid "$GID" "$USER" && \
    adduser \
    --disabled-password \
    --gecos "" \
    --ingroup "$USER" \
    --uid "$UID" \
    "$USER"

WORKDIR /app
# Copy + chown ALL data dirs (NFS/local compat)
COPY --chown=indexer:indexer --from=build /app/target/biostudies-index-service-*.jar app.jar
RUN mkdir -p /app/data/indexes /app/data /tmp/logs && \
    chown -R indexer:indexer /app /tmp

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Use shell form of ENTRYPOINT to allow environment variable expansion
ENTRYPOINT ["sh", "-c", "java -jar app.jar --index.base-dir=${INDEX_BASE_DIR} --efo.owl-filename=/app/data/efo.owl"]
