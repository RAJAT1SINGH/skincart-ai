FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress package

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN groupadd --system app && useradd --system --uid 10001 --gid app app
COPY --from=build --chown=10001:app /workspace/target/skincart-backend.jar /app/app.jar

USER 10001:app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
