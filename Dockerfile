FROM maven:3.9.9-eclipse-temurin-24 AS deps
WORKDIR /workspace

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline

FROM maven:3.9.9-eclipse-temurin-24 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q package spring-boot:repackage -DskipTests

FROM eclipse-temurin:24-jre
WORKDIR /app

COPY docker/entrypoint.sh /app/entrypoint.sh
COPY --from=build /workspace/target /app/target
RUN set -eux; \
    jar=$(ls /app/target/*.jar | grep -Ev '(original|sources|javadoc|tests)' | head -n1); \
    mv "$jar" /app/app.jar; \
    rm -rf /app/target; \
    chmod +x /app/entrypoint.sh

ENV JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
