# syntax=docker/dockerfile:1

FROM maven:3.9.16-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN mvn --batch-mode --no-transfer-progress package -DskipTests


FROM eclipse-temurin:21-jre-noble AS runtime

RUN groupadd --system app \
    && useradd \
        --system \
        --gid app \
        --home-dir /app \
        --shell /usr/sbin/nologin \
        app

WORKDIR /app

COPY --from=build \
     --chown=app:app \
     /workspace/target/*.jar \
     app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]