# Use an official OpenJDK runtime as a parent image
FROM eclipse-temurin:17-jdk

# Install FFmpeg and other system dependencies
RUN apt-get update && \
    apt-get install -y \
    ffmpeg \
    curl \
    && rm -rf /var/lib/apt/lists/* \
    && apt-get clean

# Set the working directory
WORKDIR /app

# Copy the Maven wrapper and pom.xml
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Download dependencies (this step is cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline

# Copy the rest of the app source code
COPY . .

# Build the application (skip tests for faster build)
RUN ./mvnw clean package -DskipTests

# Expose port 8080 (Spring Boot default)
EXPOSE 8080

# Set environment variables for memory optimization
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Run the Spring Boot app using the built JAR (more efficient than Maven)
CMD ["sh", "-c", "java $JAVA_OPTS -jar target/*.jar"]
