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
WORKDIR /app
COPY --from=build /app/target/biostudies-index-service-*.jar app.jar

# Create a directory for Lucene indices
RUN mkdir -p /app/indices && chown -R 1000:1000 /app/indices

# Set environment variables
ENV INDEX_BASE_DIR=/app/indices
# Default to dev profile for better logging in local Docker
ENV SPRING_PROFILES_ACTIVE=dev

# Expose the application port
EXPOSE 8080

# Run as non-root user
USER 1000:1000

# Use shell form of ENTRYPOINT to allow environment variable expansion
ENTRYPOINT ["sh", "-c", "java -jar app.jar --index.base-dir=${INDEX_BASE_DIR}"]
