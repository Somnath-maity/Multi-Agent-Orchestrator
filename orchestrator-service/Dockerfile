# Stage 1: Build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy the entire project context (parent POM, proto, and orchestrator-service)
COPY pom.xml ./pom.xml
COPY proto/ ./proto/
COPY orchestrator-service/ ./orchestrator-service/

# Build only the orchestrator-service module (and its dependencies)
RUN ./mvnw -pl orchestrator-service -am package -DskipTests || \
    mvn -pl orchestrator-service -am package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app

# Create non-root user for security
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

COPY --from=build /app/orchestrator-service/target/*.jar app.jar

RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
