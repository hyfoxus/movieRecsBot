FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/imdb-vec-*.jar app.jar

EXPOSE 8088

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","app.jar"]
