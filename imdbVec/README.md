# spring-imdb-pgvector-java24-maven

Java **24** + Spring Boot **3.5.6**, PostgreSQL + **pgvector**, DJL + ONNX.

## Run

1. Start DB: `docker compose up -d`
2. Run app: `mvn spring-boot:run`
3. Try: `curl "http://localhost:8080/search/knn?q=space%20adventure&k=10"`

## Notes

- IMDb TSVs go to `./data/imdb`
- DB files under `./volumes/postgres`
- Non-commercial datasets only for non-commercial use.
