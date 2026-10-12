# ---- Build Stage ----
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# Copy the entire project context (parent POM, proto, and all modules)
COPY pom.xml ./pom.xml
COPY proto/ ./proto/
COPY orchestrator-service/pom.xml ./orchestrator-service/pom.xml
COPY agent-execution-service/ ./agent-execution-service/

# Build only agent-execution-service and its dependencies, skip tests
RUN mvn -pl agent-execution-service -am package -DskipTests

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/agent-execution-service/target/*.jar app.jar

EXPOSE 8080 9090

ENTRYPOINT ["java", "-jar", "app.jar"]
