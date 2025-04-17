# Build Stage
FROM maven:3.8.4-openjdk-17 AS build

# Set the working directory inside the container
WORKDIR /app

# Copy the Maven project files
COPY pom.xml .

# Download dependencies to cache them for faster builds
RUN mvn dependency:go-offline

# Copy the entire source code
COPY src ./src

# Build the application without running tests
RUN mvn clean package -DskipTests

# Run Stage
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=build /app/target/nextgenmanager-0.0.1-SNAPSHOT.jar .

# Expose the application port
EXPOSE 8080:8080

# Run the application
ENTRYPOINT ["java", "-jar", "/app/nextgenmanager-0.0.1-SNAPSHOT.jar"]
