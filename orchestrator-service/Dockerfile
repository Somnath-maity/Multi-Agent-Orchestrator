# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy parent POM and module POMs first (dependency caching)
COPY pom.xml ./pom.xml
COPY orchestrator-service/pom.xml ./orchestrator-service/pom.xml
COPY agent-execution-service/pom.xml ./agent-execution-service/pom.xml

# Copy proto definitions and source code
COPY proto/ ./proto/
COPY orchestrator-service/src/ ./orchestrator-service/src/

# Build only the orchestrator-service module
RUN mvn -pl orchestrator-service -am package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

COPY --from=build /app/orchestrator-service/target/*.jar app.jar

RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
