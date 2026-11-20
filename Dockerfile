# The build stage runs on the CI runner's own architecture: the JAR is platform-independent,
# so the arm64 image never compiles Java under QEMU emulation.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw
# Dependency layer: cached until pom.xml changes.
RUN ./mvnw -q dependency:go-offline
COPY src ./src
RUN ./mvnw -q package -DskipTests
# Split the JAR into layers so a deploy only ships the small application layer.
RUN java -Djarmode=tools -jar target/salon-platform-api-*.jar extract --layers --launcher --destination /app/extracted

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring \
    && mkdir -p /app/data/attendance-photos /app/data/invoice-pdfs \
    && chown -R spring:spring /app
COPY --from=build --chown=spring:spring /app/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /app/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /app/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /app/extracted/application/ ./
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
