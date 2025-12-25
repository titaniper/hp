# Istio Service Mesh Guide

This document describes how to run the joopang microservices on Kubernetes with Istio. It covers image builds, base manifests, and Istio traffic resources.

## Prerequisites
- Kubernetes 1.27+ cluster with `kubectl` access
- [`kustomize`](https://kubectl.docs.kubernetes.io/installation/kustomize/) or `kubectl` 1.21+ (supports `-k` flags)
- [`istioctl`](https://istio.io/latest/docs/setup/getting-started/#download) 1.20+ installed locally
- Container registry credentials (e.g., GHCR) for pushing service images

## 1. Build & Push Images
Each module ships with the Spring Boot Buildpacks task, so you can create OCI images without extra Dockerfiles.

```bash
./gradlew :order-service:bootBuildImage \
  --imageName ghcr.io/<org>/joopang-order-service:local
./gradlew :coupon-service:bootBuildImage \
  --imageName ghcr.io/<org>/joopang-coupon-service:local
./gradlew :gateway-service:bootBuildImage \
  --imageName ghcr.io/<org>/joopang-gateway-service:local
# push to your registry
```

Update the image references inside `k8s/base/deployment-*.yaml` manually or run `kustomize edit set image ghcr.io/example/joopang-order-service=ghcr.io/<org>/joopang-order-service:<tag>`.

## 2. Deploy Core Services
Apply the base manifests to create databases, Redis, and the Spring services with sidecars disabled where appropriate.

```bash
kubectl apply -k k8s/base
kubectl get pods -n joopang
```

Important details:
- The namespace is labeled with `istio-injection=enabled`. MySQL and Redis pods include `sidecar.istio.io/inject: "false"` annotations so they stay outside the mesh.
- Env defaults (DB URLs, Redis host, Kafka bootstrap placeholder) live in `k8s/base/configmap-shared.yaml`. Adjust them to match your infra (e.g., point `KAFKA_BOOTSTRAP_SERVERS` to a Strimzi cluster or managed Kafka endpoint).
- Secrets currently use the local dev credentials and should be rotated for any shared environment.

## 3. Install Istio Control Plane
Install Istio with the default profile and enable Prometheus/Grafana/Zipkin if desired.

```bash
istioctl install --set profile=default -y
kubectl label namespace joopang istio-injection=enabled --overwrite
```

## 4. Apply Istio Resources
The overlay under `k8s/istio` wraps the base resources and adds:
- `Gateway` exposing HTTP traffic on `joopang.local`
- `VirtualService` routing all ingress traffic to the Spring Cloud Gateway service
- `DestinationRule`s with `ISTIO_MUTUAL` TLS and versioned subsets (v1)
- `PeerAuthentication` that enforces STRICT mTLS for workloads labeled `istio=enabled`

Apply the overlay:

```bash
kubectl apply -k k8s/istio
```

Determine the ingress address and map the host `joopang.local` locally (for example using `/etc/hosts`).

```bash
kubectl get svc istio-ingressgateway -n istio-system
curl -H "Host: joopang.local" http://<INGRESS_IP>/actuator/health
```

## 5. Traffic Management Ideas
- **Canary deploys**: Add `version: v2` labels to a second Deployment and extend the `DestinationRule` + `VirtualService` weights.
- **mTLS exceptions**: If you introduce workloads that cannot join the mesh, copy `peerauthentication.yaml` and set `mode: PERMISSIVE` with a `selector` targeting those pods.
- **Policy/Telemetry**: Attach `AuthorizationPolicy` resources or enable telemetry pipelines by pointing Envoy metrics/logs to your existing observability stack (`docker/` folder already configures Loki + InfluxDB for containers; export them via Istio as needed).

## 6. Troubleshooting
- `istioctl analyze -n joopang` highlights missing virtual services, TLS issues, or pod annotations.
- If pods stay in `CrashLoopBackOff`, check database readiness or Kafka connectivity (the manifests do not deploy Kafka; install Strimzi/Redpanda and update the shared ConfigMap).
- For ingress issues, run `istioctl proxy-status` and inspect Envoy config dumps via `istioctl proxy-config routes <pod>`.

## Next Steps
- Automate image builds and `kubectl apply -k k8s/istio` via CI/CD
- Add `HorizontalPodAutoscaler` definitions per workload using Istio metrics (RPS, latency)
- Extend the VirtualService with retries/timeouts per route and attach `RequestAuthentication`/`AuthorizationPolicy` once an identity provider is available
