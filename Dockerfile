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
# Fixed numeric uid/gid: Kubernetes can only enforce runAsNonRoot when USER is numeric
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app
# One layer per change frequency: dependencies rarely change, application classes change on every build
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
USER 10001:10001
# JSON (ECS) logs in containers, for log aggregators; set LOGGING_STRUCTURED_FORMAT_CONSOLE= for plain text
ENV SERVER_PORT=8080 \
    MANAGEMENT_SERVER_PORT=9090 \
    JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75" \
    LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs
# 8080: API. 9090: actuator (probes, prometheus); publish it only to the monitoring/orchestration network
EXPOSE 8080 9090
# The runtime image has no curl/wget; bash's /dev/tcp is enough to check the readiness status code.
# Timeout must exceed DB_CONNECTION_TIMEOUT_MS: with the DB down, readiness takes that long to answer 503
HEALTHCHECK --interval=15s --timeout=7s --start-period=60s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/${MANAGEMENT_SERVER_PORT} && printf "GET /actuator/health/readiness HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && head -1 <&3 | grep -q " 200 "'
ENTRYPOINT ["java", "-jar", "app.jar"]
