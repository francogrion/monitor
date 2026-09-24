# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
# Tests need a real Postgres (see README), so they run in the test pipeline rather than in the image build
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/*.jar extract --layers --destination target/extracted \
 && mv target/extracted/application/*.jar target/extracted/application/app.jar

FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
# One layer per change frequency: dependencies rarely change, application classes change on every build
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
USER app
ENV SERVER_PORT=8080 \
    JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
# The runtime image has no curl/wget; bash's /dev/tcp is enough to check the readiness status code
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/${SERVER_PORT} && printf "GET /actuator/health/readiness HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && head -1 <&3 | grep -q " 200 "'
ENTRYPOINT ["java", "-jar", "app.jar"]
