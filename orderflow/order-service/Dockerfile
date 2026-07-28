FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN unset MAVEN_CONFIG \
    && ./mvnw -B -ntp -pl order-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl \
    && addgroup -S orderflow \
    && adduser -S -G orderflow -h /app orderflow
WORKDIR /app
COPY --from=build --chown=orderflow:orderflow /workspace/order-service/target/app.jar /app/app.jar
USER orderflow
EXPOSE 8085
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=8s --start-period=30s --retries=15 \
  CMD curl --fail --silent http://localhost:8085/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
