FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/imdbvec-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","app.jar"]
