# Use an official OpenJDK runtime as a parent image
FROM eclipse-temurin:17-jdk

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

# Run the Spring Boot app
CMD ["./mvnw", "spring-boot:run"]
