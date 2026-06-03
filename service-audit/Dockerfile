FROM eclipse-temurin:17-jre-alpine
LABEL maintainer="classroom-booking-system"

WORKDIR /app

COPY target/service-audit-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]

