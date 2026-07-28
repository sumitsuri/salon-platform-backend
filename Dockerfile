FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw
COPY src ./src
RUN ./mvnw -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring \
    && mkdir -p /app/data/attendance-photos /app/data/invoice-pdfs \
    && chown -R spring:spring /app
COPY --from=build --chown=spring:spring /app/target/salon-platform-api-*.jar app.jar
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
