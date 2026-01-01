FROM maven:3.9.9-eclipse-temurin-24 AS build
WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY apps/movieRecBot/pom.xml apps/movieRecBot/pom.xml
COPY apps/movieRecBot/src apps/movieRecBot/src
COPY apps/imdb-vec/pom.xml apps/imdb-vec/pom.xml
COPY apps/imdb-vec/src apps/imdb-vec/src
COPY apps/normalizer/pom.xml apps/normalizer/pom.xml
COPY apps/normalizer/src apps/normalizer/src
COPY apps/movie-mcp-server/pom.xml apps/movie-mcp-server/pom.xml
COPY apps/movie-mcp-server/src apps/movie-mcp-server/src
COPY .git .git

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl apps/movie-mcp-server -am -DskipTests package spring-boot:repackage

FROM eclipse-temurin:24-jre
WORKDIR /app
COPY apps/movie-mcp-server/docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh
COPY --from=build /workspace/apps/movie-mcp-server/target/*.jar /app/app.jar
ENV JAVA_OPTS=""
EXPOSE 8082
ENTRYPOINT ["/app/entrypoint.sh"]
