# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q test dependency:copy-dependencies -DincludeScope=runtime

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/classes ./classes
COPY --from=build /workspace/target/dependency ./dependency
EXPOSE 8080
ENTRYPOINT ["java", "-cp", "classes:dependency/*", "com.workbench.reconciliation.WorkbenchApplication"]