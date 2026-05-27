# Multi-stage build for api-gateway-camel
# Build: nerdctl --namespace k8s.io build -t classroom/api-gateway:latest ./api-gateway-camel
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
# The JAR is built by the parent Maven build; copy the already-built artifact
COPY target/*.jar app.jar

# Stage 2: runtime (minimal JRE)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=builder /build/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

