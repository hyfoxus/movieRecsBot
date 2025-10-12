#
# Multi-stage build that produces a runnable Spring Boot jar image.
# Build with: docker build -t movie-recs-bot .
# Run with: docker run --rm -p 8080:8080 --env-file .env movie-recs-bot
#

FROM maven:3.9.9-eclipse-temurin-24 AS build
WORKDIR /workspace

# Copy project files
COPY pom.xml .
COPY src ./src
COPY mvnw .
COPY .mvn ./.mvn

RUN chmod +x mvnw
RUN ./mvnw -B -DskipTests clean package

FROM eclipse-temurin:24-jdk AS runtime
WORKDIR /app

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]
