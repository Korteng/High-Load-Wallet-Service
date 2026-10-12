FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
# Russian Trusted Root/Sub CA (public certificates) so the JVM trusts the GigaChat API endpoints
COPY certs/ /tmp/certs/
RUN for f in /tmp/certs/*.cer; do keytool -importcert -cacerts -storepass changeit -noprompt -alias "$(basename "$f" .cer)" -file "$f"; done && rm -rf /tmp/certs
COPY --from=build /build/target/wallet-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
