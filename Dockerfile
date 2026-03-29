# Build Stage
FROM maven:3.8.4-openjdk-17 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src

RUN mvn clean package -DskipTests

# Run Stage
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY --from=build /app/target/nextgenmanager-0.0.1-SNAPSHOT.jar .

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/nextgenmanager-0.0.1-SNAPSHOT.jar"]
