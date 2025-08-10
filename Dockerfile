# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Cache deps first
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

# Now copy sources and build
COPY src ./src
# If you rely on tests in CI only, skipping tests here speeds up deploys
RUN mvn -B -DskipTests clean package

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Install ffmpeg and curl for health checks
RUN apt-get update \
 && apt-get install -y --no-install-recommends ffmpeg curl \
 && rm -rf /var/lib/apt/lists/*

# Non-root user
RUN useradd -r -u 1001 appuser
COPY --from=build /workspace/target/*.jar /app/app.jar
RUN chown -R appuser:appuser /app
USER appuser

# Spring Boot default is 8080, but Render passes PORT
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Health check for better deployment monitoring
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8080}/health || exit 1

# Bind to Render's PORT if provided, else 8080
CMD ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar"]