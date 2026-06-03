# Deployment, Testing & Operations Guide

This document covers everything needed to build, deploy, and test the Classroom Booking System from scratch.

RabbitMQ is deployed **exclusively via Kubernetes** alongside the microservices — there is no docker-compose file.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Start](#quick-start)
3. [Build — Maven JARs](#build--maven-jars)
4. [Build — Docker Images](#build--docker-images)
5. [Deploy to Rancher Desktop (Kubernetes)](#deploy-to-rancher-desktop-kubernetes)
6. [Accessing Services (LoadBalancer)](#accessing-services-loadbalancer--no-port-forwarding-needed)
7. [Manual API Testing with curl](#manual-api-testing-with-curl)
8. [Running Unit Tests](#running-unit-tests)
9. [Running the Cucumber BDD Suite](#running-the-cucumber-bdd-suite)
10. [Observability — Logs & RabbitMQ UI](#observability--logs--rabbitmq-ui)
11. [Tearing Down](#tearing-down)
12. [Troubleshooting](#troubleshooting)

---

## Prerequisites

| Tool | Minimum Version | Purpose |
|------|----------------|---------|
| Java JDK | 17 | Build and run services |
| Apache Maven | 3.9 | Multi-module build |
| Rancher Desktop | 1.12+ | Local Kubernetes + containerd (`nerdctl`) |
| kubectl | 1.28+ | Deploy & inspect manifests |
| curl | Any | API testing |

**Rancher Desktop configuration:**
- Container engine: **containerd** (not dockerd)
- Kubernetes: **enabled**
- Traefik ingress: **enabled** (default)

---

## Quick Start

From a clean checkout — build everything, push images into containerd, and deploy the full stack (RabbitMQ + all four microservices) to Kubernetes in one go:

```powershell
# 1 — Build all JARs
mvn clean package -DskipTests

# 2 — Build all Docker images into the k8s.io containerd namespace
nerdctl --namespace k8s.io build -t classroom/api-gateway:latest          ./api-gateway-camel
nerdctl --namespace k8s.io build -t classroom/service-booking:latest      ./service-booking
nerdctl --namespace k8s.io build -t classroom/service-availability:latest ./service-availability
nerdctl --namespace k8s.io build -t classroom/service-notification:latest ./service-notification
nerdctl --namespace k8s.io build -t classroom/service-audit:latest        ./service-audit

# 3 — Deploy everything (namespace, RabbitMQ, microservices, ingress, HPA)
kubectl apply -k k8s/

# 4 — Wait for all pods to be ready
kubectl wait --for=condition=Ready pod --all -n classroom-booking --timeout=180s

# 5 — All services are now reachable on localhost (LoadBalancer, no port-forward needed)
#     API Gateway:    http://localhost:8080
#     Booking:        http://localhost:8081
#     Availability:   http://localhost:8082
#     Notification:   http://localhost:8083
#     Audit:          http://localhost:8084
#     RabbitMQ AMQP:  localhost:5672
```

---

## Build — Maven JARs

Always build from the **project root** so the multi-module reactor resolves inter-module dependencies correctly.

```powershell
# Full clean build — skip unit tests
mvn clean package -DskipTests

# Full clean build — run unit tests
mvn clean package

# Rebuild only the API gateway and its dependency
mvn package -pl common-lib,api-gateway-camel -DskipTests

# Verbose output
mvn clean package -DskipTests -X
```

After a successful build, fat JARs are at:

| Module | JAR Location |
|--------|-------------|
| api-gateway-camel | `api-gateway-camel/target/api-gateway-camel-1.0.0-SNAPSHOT.jar` |
| service-booking | `service-booking/target/service-booking-1.0.0-SNAPSHOT.jar` |
| service-availability | `service-availability/target/service-availability-1.0.0-SNAPSHOT.jar` |
| service-notification | `service-notification/target/service-notification-1.0.0-SNAPSHOT.jar` |
| service-audit | `service-audit/target/service-audit-1.0.0-SNAPSHOT.jar` |

---

## Build — Docker Images

Images must be built into the **`k8s.io` containerd namespace** so Kubernetes can find them with `imagePullPolicy: Never`.

```powershell
# Build all five service images from the project root
nerdctl --namespace k8s.io build -t classroom/api-gateway:latest          ./api-gateway-camel
nerdctl --namespace k8s.io build -t classroom/service-booking:latest      ./service-booking
nerdctl --namespace k8s.io build -t classroom/service-availability:latest ./service-availability
nerdctl --namespace k8s.io build -t classroom/service-notification:latest ./service-notification
nerdctl --namespace k8s.io build -t classroom/service-audit:latest        ./service-audit

# Verify images are present
nerdctl --namespace k8s.io images | Select-String "classroom"
```

> **Credential error fix:** If you see `error getting credentials – exit status 22`, run this once:
> ```powershell
> [System.IO.File]::WriteAllText(
>   "\\wsl$\rancher-desktop\root\.docker\config.json",
>   '{"auths":{"registry-1.docker.io":{}},"credsStore":"","credHelpers":{}}',
>   [System.Text.Encoding]::ASCII)
> ```

RabbitMQ uses the public `rabbitmq:3.13-management` image and MongoDB uses the public `mongo:7` image — Kubernetes pulls them automatically; no manual build step needed.

---

## Deploy to Rancher Desktop (Kubernetes)

### Kubernetes resource inventory

`kubectl apply -k k8s/` creates these resources in the `classroom-booking` namespace:

| Manifest | Resources created |
|----------|------------------|
| `namespace.yaml` | `Namespace: classroom-booking` |
| `rabbitmq-secret.yaml` | `Secret: rabbitmq-secret` (base64 credentials) |
| `mongodb-secret.yaml` | `Secret: mongodb-secret` (MongoDB connection URI + credentials) |
| `configmap.yaml` | `ConfigMap: classroom-config` (service URLs, RabbitMQ host, route-slip pipeline) |
| `rabbitmq-pvc.yaml` | `PersistentVolumeClaim: rabbitmq-pvc` (2 Gi) |
| `rabbitmq-deployment.yaml` | `Deployment: rabbitmq` (1 replica, `rabbitmq:3.13-management`) |
| `rabbitmq-service.yaml` | `Service: rabbitmq-service` (ClusterIP :5672) + `rabbitmq-management-svc` (NodePort :30672) |
| `mongodb-deployment.yaml` | `Deployment: mongodb` (1 replica, `mongo:7`) + `Service: mongodb-service` (ClusterIP :27017) |
| `service-booking.yaml` | `Deployment + Service: service-booking` (LoadBalancer :8081) |
| `service-availability.yaml` | `Deployment + Service: service-availability` (LoadBalancer :8082, H2 in-memory) |
| `service-notification.yaml` | `Deployment + Service: service-notification` (LoadBalancer :8083) |
| `service-audit-deployment.yaml` | `Deployment: service-audit` (:8084, MongoDB-backed) |
| `service-audit-service.yaml` | `Service: service-audit` (LoadBalancer :8084) |
| `api-gateway-deployment.yaml` | `Deployment: api-gateway` (2 replicas) |
| `api-gateway-service.yaml` | `Service: api-gateway` (LoadBalancer :8080) |
| `ingress.yaml` | `Ingress: classroom-ingress` (Traefik, routes `/bookings` and `/actuator`) |
| `hpa.yaml` | `HorizontalPodAutoscaler: api-gateway-hpa` (min 2 / max 6 replicas) |

### First-time deployment

```powershell
kubectl apply -k k8s/

# Watch pods come up  (Ctrl+C when all show 1/1 Running)
kubectl get pods -n classroom-booking -w
```

Expected steady state:

```
NAME                                    READY   STATUS    RESTARTS
api-gateway-xxxxx                       1/1     Running   0
api-gateway-xxxxx                       1/1     Running   0
mongodb-xxxxx                           1/1     Running   0
rabbitmq-xxxxx                          1/1     Running   0
service-audit-xxxxx                     1/1     Running   0
service-availability-xxxxx              1/1     Running   0
service-booking-xxxxx                   1/1     Running   0
service-notification-xxxxx              1/1     Running   0
```

> **Startup order:** Spring AMQP retries the RabbitMQ connection automatically (3 attempts, exponential back-off). If a microservice pod starts before RabbitMQ is ready, it will reconnect once RabbitMQ is up — no manual intervention required.

### Rebuild and redeploy after code changes

```powershell
# 1 — Rebuild JARs
mvn clean package -DskipTests

# 2 — Rebuild affected images
nerdctl --namespace k8s.io build -t classroom/api-gateway:latest          ./api-gateway-camel
nerdctl --namespace k8s.io build -t classroom/service-booking:latest      ./service-booking
nerdctl --namespace k8s.io build -t classroom/service-availability:latest ./service-availability
nerdctl --namespace k8s.io build -t classroom/service-notification:latest ./service-notification
nerdctl --namespace k8s.io build -t classroom/service-audit:latest        ./service-audit

# 3 — Rolling restart to pick up new images
kubectl rollout restart deployment/api-gateway \
                          deployment/service-booking \
                          deployment/service-availability \
                          deployment/service-notification \
                          deployment/service-audit \
                          -n classroom-booking

# 4 — Wait for completion
kubectl rollout status deployment/api-gateway \
                        deployment/service-booking \
                        deployment/service-availability \
                        deployment/service-notification \
                        deployment/service-audit \
                        -n classroom-booking --timeout=120s
```

### Useful kubectl commands

```powershell
# All resources in the namespace
kubectl get all -n classroom-booking

# Stream logs
kubectl logs -n classroom-booking deployment/api-gateway          -f
kubectl logs -n classroom-booking deployment/service-booking      -f
kubectl logs -n classroom-booking deployment/service-availability -f
kubectl logs -n classroom-booking deployment/service-notification -f
kubectl logs -n classroom-booking deployment/rabbitmq             -f

# Describe a pod (diagnose startup problems)
kubectl describe pod -n classroom-booking <pod-name>

# HPA status (auto-scaling)
kubectl get hpa -n classroom-booking

# ConfigMap values
kubectl get configmap classroom-config -n classroom-booking -o yaml
```

---

## Accessing Services (LoadBalancer — No Port Forwarding Needed)

All services are exposed as `type: LoadBalancer`. On Rancher Desktop, each LoadBalancer service is automatically bound to **localhost** on its configured port — no `kubectl port-forward` required.

### Service endpoints (available immediately after deploy)

| Service | URL | Purpose |
|---------|-----|---------|
| API Gateway | `http://localhost:8080` | Main entry point for bookings |
| Booking Service | `http://localhost:8081` | Internal booking CRUD |
| Availability Service | `http://localhost:8082` | Slot availability checks |
| Notification Service | `http://localhost:8083` | Notification log & test endpoints |
| Audit Service | `http://localhost:8084` | Audit trail (MongoDB-backed) |
| RabbitMQ AMQP | `localhost:5672` | Messaging (used by BDD tests) |
| RabbitMQ Management UI | `http://localhost:30672` | Web UI (NodePort, guest/guest) |
| Ingress (Traefik) | `http://localhost/bookings` | Alternative access via port 80 |

### Verify connectivity

```powershell
# Quick health check — all services
curl.exe -s http://localhost:8080/actuator/health
curl.exe -s http://localhost:8081/actuator/health
curl.exe -s http://localhost:8082/actuator/health
curl.exe -s http://localhost:8083/actuator/health
curl.exe -s http://localhost:8084/actuator/health
```

> **Note:** If a port is already in use by another process on the host, the LoadBalancer binding will fail silently. Use `netstat -ano | findstr ":<port>"` to check and free the port.

> **Fallback — Port Forwarding:** If LoadBalancer doesn't work (e.g., non-Rancher Desktop clusters), you can still use manual port-forwards:
> ```powershell
> kubectl port-forward svc/api-gateway          8080:8080 -n classroom-booking
> kubectl port-forward svc/service-booking      8081:8081 -n classroom-booking
> kubectl port-forward svc/service-availability 8082:8082 -n classroom-booking
> kubectl port-forward svc/service-notification 8083:8083 -n classroom-booking
> kubectl port-forward svc/service-audit        8084:8084 -n classroom-booking
> kubectl port-forward svc/rabbitmq-service     5672:5672 -n classroom-booking
> ```

---

## Manual API Testing with curl

All examples assume the API Gateway is reachable at `localhost:8080` (LoadBalancer, no port-forward needed).

### Helper function (avoids PowerShell BOM / escaping issues)

```powershell
function Post-Booking($json) {
    [System.IO.File]::WriteAllText(
        "$env:TEMP\booking.json", $json,
        (New-Object System.Text.UTF8Encoding $false))
    curl.exe -s -o "$env:TEMP\resp.json" -w "HTTP %{http_code}`n" `
        -X POST http://localhost:8080/bookings `
        -H "Content-Type: application/json" `
        --data-binary "@$env:TEMP\booking.json"
    Get-Content "$env:TEMP\resp.json"
}
```

### Health check

```powershell
curl.exe -s http://localhost:8080/actuator/health
# {"status":"UP","components":{"rabbit":{"status":"UP"},...}}
```

### Happy-path booking

```powershell
Post-Booking '{
  "classroomId": "CR-101",
  "date":        "2026-07-01",
  "timeSlot": { "startTime": "09:00", "endTime": "10:00" },
  "requestedBy": "alice@example.com"
}'
# HTTP 202  {"bookingId":"BK-xxxxxxxx","status":"PENDING",...}
```

### Conflicting booking (same classroom + slot)

```powershell
Post-Booking '{
  "classroomId": "CR-101",
  "date":        "2026-07-01",
  "timeSlot": { "startTime": "09:00", "endTime": "10:00" },
  "requestedBy": "bob@example.com"
}'
# HTTP 202  PENDING  →  async REJECTED (slot taken by alice)
```

### Adjacent slot (allowed — non-overlapping)

```powershell
Post-Booking '{
  "classroomId": "CR-101",
  "date":        "2026-07-01",
  "timeSlot": { "startTime": "10:00", "endTime": "11:00" },
  "requestedBy": "carol@example.com"
}'
# HTTP 202  PENDING  →  async CONFIRMED
```

### Validation errors

```powershell
# Missing classroomId  →  400
Post-Booking '{"date":"2026-07-01","timeSlot":{"startTime":"09:00","endTime":"10:00"},"requestedBy":"x@x.com"}'

# Missing requestedBy  →  400
Post-Booking '{"classroomId":"CR-101","date":"2026-07-01","timeSlot":{"startTime":"09:00","endTime":"10:00"}}'

# endTime before startTime  →  400
Post-Booking '{"classroomId":"CR-101","date":"2026-07-01","timeSlot":{"startTime":"11:00","endTime":"09:00"},"requestedBy":"x@x.com"}'

# Invalid date format (dd-MM-yyyy)  →  400
Post-Booking '{"classroomId":"CR-101","date":"01-07-2026","timeSlot":{"startTime":"09:00","endTime":"10:00"},"requestedBy":"x@x.com"}'
```

### Verify async results via notification service

```powershell
# Notification service is directly accessible via LoadBalancer on port 8083

curl.exe -s http://localhost:8083/test/notifications/summary
# {"total":N,"confirmations":X,"rejections":Y}

curl.exe -s http://localhost:9083/test/notifications
# Full list of all notifications (type, bookingId, recipient, message)
```

---

## Running Unit Tests

Each module has self-contained unit tests — **no running services required**.

```powershell
# All modules
mvn test

# Specific module
mvn test -pl service-availability
mvn test -pl service-notification
mvn test -pl service-booking
mvn test -pl common-lib

# Single test class
mvn test -pl service-availability -Dtest=AvailabilityServiceTest

# Skip during package
mvn package -DskipTests
```

Reports at `<module>/target/surefire-reports/`.

---

## Running the Cucumber BDD Suite

The BDD suite makes **live HTTP and AMQP calls**. All services must be running in Kubernetes. Since all services use `type: LoadBalancer`, they are directly accessible on localhost — **no port forwarding required**.

### Step 1 — Deploy (if not already running)

```powershell
kubectl apply -k k8s/
kubectl wait --for=condition=Ready pod --all -n classroom-booking --timeout=180s
```

### Step 2 — Run the suite

```powershell
# From the project root:
mvn test -pl integration-tests
```

> **No port-forwarding step!** LoadBalancer services bind directly to localhost on ports 8080–8084 and 5672.

### Filtering by tag

```powershell
# Smoke tests only  (~15 scenarios, fastest sanity check)
mvn test -pl integration-tests -Dcucumber.filter.tags="@smoke"

# API Gateway scenarios
mvn test -pl integration-tests -Dcucumber.filter.tags="@api"

# Availability overlap detection
mvn test -pl integration-tests -Dcucumber.filter.tags="@availability"

# End-to-end flows
mvn test -pl integration-tests -Dcucumber.filter.tags="@e2e"

# RabbitMQ messaging flow
mvn test -pl integration-tests -Dcucumber.filter.tags="@messaging"

# Notification service
mvn test -pl integration-tests -Dcucumber.filter.tags="@notification"

# Full booking flow (happy path + conflicts)
mvn test -pl integration-tests -Dcucumber.filter.tags="@booking"

# Skip slow resilience tests
mvn test -pl integration-tests -Dcucumber.filter.tags="not @resilience"

# Combine tags (AND)
mvn test -pl integration-tests -Dcucumber.filter.tags="@smoke and @booking"
```

### Test reports

```
integration-tests/target/cucumber-reports/
├── cucumber.html    ← Human-readable (open in any browser)
├── cucumber.json    ← Machine-readable (CI dashboards, Jira X-Ray)
└── cucumber.xml     ← JUnit format (Jenkins, GitHub Actions)
```

---

## Observability — Logs & RabbitMQ UI

### Stream service logs

```powershell
kubectl logs -n classroom-booking deployment/api-gateway          -f
kubectl logs -n classroom-booking deployment/service-booking      -f
kubectl logs -n classroom-booking deployment/service-availability -f
kubectl logs -n classroom-booking deployment/service-notification -f
kubectl logs -n classroom-booking deployment/rabbitmq             -f

# From a specific pod
kubectl logs -n classroom-booking <pod-name> -f

# Previous container logs (pod restarted)
kubectl logs -n classroom-booking <pod-name> --previous
```

### RabbitMQ Management UI

**Via NodePort** (no extra port-forward needed when cluster is running):
```
http://localhost:30672   →   guest / guest
```

**Via port-forward** (alternative):
```powershell
kubectl port-forward svc/rabbitmq-management-svc 15672:15672 -n classroom-booking
# http://localhost:15672  →  guest / guest
```

From the UI you can:
- Monitor queue depths (`booking.requested`, `booking.confirmed`, `booking.rejected`)
- Inspect message payloads
- Publish test messages manually
- View exchange bindings and routing topology
- Check consumer connections

---

## Tearing Down

### Remove resources, keep RabbitMQ data volume

```powershell
kubectl delete -k k8s/
```

### Remove everything including the persistent volume

```powershell
kubectl delete -k k8s/
kubectl delete pvc rabbitmq-pvc -n classroom-booking
```

### Delete the entire namespace

```powershell
kubectl delete namespace classroom-booking
```

---

## Troubleshooting

### Pods stuck in `ImagePullBackOff` or `ErrImageNeverPull`

Images must be in containerd's `k8s.io` namespace, not the default one.

```powershell
# Check
nerdctl --namespace k8s.io images | Select-String "classroom"

# Rebuild if missing
nerdctl --namespace k8s.io build -t classroom/api-gateway:latest ./api-gateway-camel
```

### Pods stuck in `CrashLoopBackOff`

```powershell
kubectl logs -n classroom-booking <pod-name> --previous

# Common causes:
# - RabbitMQ not ready yet  →  Spring AMQP retries; wait 30-60 s
# - Wrong env var           →  check kubectl describe pod / configmap
```

### Microservices fail to connect to RabbitMQ

The ConfigMap sets `RABBITMQ_HOST=rabbitmq-service`. Verify the service and pod exist:

```powershell
kubectl get svc rabbitmq-service -n classroom-booking
kubectl get pod -l app=rabbitmq -n classroom-booking
```

Spring AMQP will retry automatically once RabbitMQ is ready — no restart needed.

### `curl` returns empty output in PowerShell

Always use the actual curl binary, not the PowerShell alias:

```powershell
curl.exe -s http://localhost:8080/actuator/health
```

### JSON body errors in PowerShell

Write to a temp file to avoid BOM and quote-escaping issues:

```powershell
$json = '{"classroomId":"CR-101","date":"2026-07-01","timeSlot":{"startTime":"09:00","endTime":"10:00"},"requestedBy":"alice@example.com"}'
[System.IO.File]::WriteAllText("$env:TEMP\b.json", $json, (New-Object System.Text.UTF8Encoding $false))
curl.exe -s -X POST http://localhost:8080/bookings -H "Content-Type: application/json" --data-binary "@$env:TEMP\b.json"
```

### Port already in use (LoadBalancer binding fails)

```powershell
# Find what's using the port
netstat -ano | findstr ":8080"

# Kill the process or stop the conflicting application, then re-apply:
kubectl delete svc api-gateway -n classroom-booking
kubectl apply -k k8s/
```

### Services unreachable after `kubectl rollout restart`

LoadBalancer bindings persist across rollouts (they're tied to the Service, not the Pod). If a service is briefly unavailable during restart, wait for the new pods:

```powershell
kubectl rollout status deployment/api-gateway -n classroom-booking --timeout=120s
```

### BDD tests fail with `Connection refused` on port 5672

The test profile expects RabbitMQ at `localhost:5672` via LoadBalancer. Verify the service has an external IP:

```powershell
kubectl get svc rabbitmq-service -n classroom-booking
# EXTERNAL-IP should show an IP (e.g., 192.168.127.2), not <pending>

# If still failing, check if another process holds port 5672:
netstat -ano | findstr ":5672"

# Fallback — manual port-forward:
kubectl port-forward svc/rabbitmq-service 5672:5672 -n classroom-booking
```

### Invalid date format

Dates must use ISO format `yyyy-MM-dd`:

| Value | Result |
|-------|--------|
| `"2026-07-01"` | ✅ |
| `"01-07-2026"` | ❌ `400` |
| `"07/01/2026"` | ❌ `400` |
