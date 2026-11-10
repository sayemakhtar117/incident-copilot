# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Set MAVEN_ARGS=-Paws to include AWS SSM Parameter Store support (see docs/SECRETS.md)
ARG MAVEN_ARGS=""
COPY pom.xml .
RUN mvn -B -q $MAVEN_ARGS dependency:go-offline
COPY src src
RUN mvn -B -q $MAVEN_ARGS -DskipTests package

# ---- run ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 app
COPY --from=build /app/target/app.jar app.jar
USER app
# Tuned for small free-tier containers (512 MB RAM)
ENV JAVA_OPTS="-Xmx320m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
