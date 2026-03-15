FROM gradle:8.14.3-jdk21 AS build

WORKDIR /src
COPY . .
RUN gradle --no-daemon shadowJar

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /src/build/libs/openhands-chatmock-adapter.jar /app/adapter.jar

EXPOSE 5000

ENTRYPOINT ["java", "-jar", "/app/adapter.jar"]
