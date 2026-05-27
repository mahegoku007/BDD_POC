FROM eclipse-temurin:17-jdk-alpine AS builder
# Build: nerdctl --namespace k8s.io build -t classroom/service-notification:latest ./service-notification
WORKDIR /build
COPY target/*.jar app.jar

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=builder /build/app.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

