FROM maven:3.9.9-eclipse-temurin-24 AS deps
WORKDIR /workspace

COPY pom.xml ./
COPY imdbVec/pom.xml imdbVec/
COPY movieRecBot/pom.xml movieRecBot/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -am -pl movieRecBot -DskipTests dependency:go-offline

FROM maven:3.9.9-eclipse-temurin-24 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -am -pl movieRecBot -DskipTests package spring-boot:repackage

FROM eclipse-temurin:24-jre
WORKDIR /app

COPY movieRecBot/docker/entrypoint.sh /app/entrypoint.sh
COPY --from=build /workspace/movieRecBot/target /app/target
RUN set -eux; \
    jar=$(ls /app/target/*.jar | grep -Ev '(original|sources|javadoc|tests)' | head -n1); \
    mv "$jar" /app/app.jar; \
    rm -rf /app/target

RUN chmod +x /app/entrypoint.sh

ENV JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]