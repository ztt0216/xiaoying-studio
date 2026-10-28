# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=UTC STORAGE_DIR=/data JAVA_TOOL_OPTIONS="-Djava.awt.headless=true"
RUN mkdir -p /data
COPY --from=build /src/target/studio-backend.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
