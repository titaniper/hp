# 🕸️ 서비스 메시와 Istio (Service Mesh & Istio)

## 1. 서비스 메시 (Service Mesh)란?

### 1.1 개념

마이크로서비스 간의 통신(네트워크)을 담당하는 인프라 계층입니다. 애플리케이션 코드의 변경 없이 서비스 간의 통신을 제어, 관측, 보안 처리할 수 있게 해줍니다.

### 1.2 등장 배경

MSA(Microservices Architecture)가 복잡해지면서 서비스 간 통신 관리(재시도, 타임아웃, 서킷 브레이커, 인증 등) 로직이 각 애플리케이션 코드에 중복 구현되는 문제가 발생했습니다. 이를 인프라 레벨로 분리하기 위해 등장했습니다.

---

## 2. Istio 아키텍처

Istio는 가장 대표적인 오픈소스 서비스 메시 구현체입니다.

```mermaid
graph TD
    subgraph Control Plane [Control Plane (Istiod)]
        Discovery[Service Discovery]
        Config[Configuration]
        Certs[Certificates]
    end

    subgraph Data Plane
        subgraph Service A Pod
            AppA[Service A]
            ProxyA[Envoy Proxy<br/>(Sidecar)]
        end
        
        subgraph Service B Pod
            AppB[Service B]
            ProxyB[Envoy Proxy<br/>(Sidecar)]
        end
    end

    AppA <-->|Localhost| ProxyA
    ProxyA <-->|mTLS| ProxyB
    ProxyB <-->|Localhost| AppB
    
    Control Plane -.->|Config Updates| ProxyA
    Control Plane -.->|Config Updates| ProxyB
```

### 2.1 데이터 플레인 (Data Plane) - Envoy Proxy

- **사이드카(Sidecar) 패턴**: 각 애플리케이션 컨테이너 옆에 프록시 컨테이너(Envoy)를 배치합니다.
- 모든 인바운드/아웃바운드 트래픽을 가로채서 제어합니다.

### 2.2 컨트롤 플레인 (Control Plane) - Istiod

- 프록시들에게 라우팅 규칙, 보안 정책 등을 배포하고 관리합니다.

---

## 3. Istio의 핵심 기능

### 3.1 트래픽 관리 (Traffic Management)

- **라우팅 제어**: 헤더 값, URL 경로 등에 따라 트래픽을 특정 버전의 서비스로 라우팅.
- **카나리 배포 (Canary Deployment)**: 트래픽의 5%는 v2로, 95%는 v1으로 보내는 등 점진적 배포 가능.
- **Resilience**: 재시도(Retry), 타임아웃(Timeout), 서킷 브레이커(Circuit Breaker)를 코드 수정 없이 설정만으로 적용.

### 3.2 보안 (Security)

- **mTLS (Mutual TLS)**: 서비스 간 통신을 자동으로 암호화하고 상호 인증합니다.
- **인증/인가 (Authentication/Authorization)**: 서비스 간 접근 제어 정책(Policy) 적용.

### 3.3 관찰 가능성 (Observability)

- 프록시를 통과하는 모든 트래픽에 대한 메트릭, 로그, 트레이싱 정보를 자동으로 수집하여 Prometheus, Jaeger 등으로 전송합니다.

---

## 4. 예시: VirtualService 설정 (Traffic Splitting)

```yaml
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: reviews-route
spec:
  hosts:
  - reviews
  http:
  - route:
    - destination:
        host: reviews
        subset: v1
      weight: 75
    - destination:
        host: reviews
        subset: v2
      weight: 25
```

위 설정은 `reviews` 서비스로 가는 트래픽의 75%를 v1 버전으로, 25%를 v2 버전으로 분산시킵니다.
