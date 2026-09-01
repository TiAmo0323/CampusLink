FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S campuslink && adduser -S campuslink -G campuslink
WORKDIR /app
COPY --from=build /workspace/target/campuslink-backend-1.0.0.jar app.jar
RUN mkdir -p /app/uploads && chown -R campuslink:campuslink /app
USER campuslink
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
