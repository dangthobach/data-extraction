# Backpressure Review & Next Steps Plan

## 1. Integration Service - Backpressure Analysis

### Current Implementation Status

| Feature | Status | Notes |
|---------|--------|-------|
| Rate Limiting (Daily) | ✅ Done | 100 req/day per system via Redis Lua |
| File Size Limit | ✅ Done | `max-file-size: 100MB` configured |
| Request Timeout | ✅ Done | Tomcat connection-timeout: 30s |
| Connection Pool Limits | ✅ Done | Redis Lettuce pool configured |
| RabbitMQ Publisher Confirms | ✅ Done | Correlated confirms with 5s timeout |
| Circuit Breaker | ✅ Done | Resilience4j on MinIO & RabbitMQ |
| Bulkhead Pattern | ✅ Done | Upload: 30 concurrent, Trigger: 50 concurrent |
| Request Queue/Throttling | ✅ Done | Tomcat accept-count: 100 |

### Backpressure Gaps to Address

#### 1.1 File Upload Size Limit
**Problem**: Large files can overwhelm memory and MinIO.
**Solution**: Add `spring.servlet.multipart.max-file-size` config.

#### 1.2 RabbitMQ Publisher Confirms
**Problem**: If RabbitMQ is slow/down, messages may be lost.
**Solution**: Enable publisher confirms with correlation data.

#### 1.3 Circuit Breaker (Resilience4j)
**Problem**: If MinIO or RabbitMQ is down, all requests fail.
**Solution**: Add CircuitBreaker with fallback responses.

#### 1.4 Semaphore/Bulkhead
**Problem**: Burst traffic can exhaust thread pool.
**Solution**: Limit concurrent uploads per endpoint.

#### 1.5 Timeout Configuration
**Problem**: Slow uploads can block threads.
**Solution**: Configure timeouts for MinIO and RabbitMQ operations.

---

## 2. Proposed Backpressure Enhancements

### 2.1 Configuration Changes (application.yml)
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
  rabbitmq:
    template:
      mandatory: true
    publisher-confirms: true
    publisher-returns: true
    connection-timeout: 10s

server:
  tomcat:
    max-threads: 200
    accept-count: 100
    connection-timeout: 30s

resilience4j:
  circuitbreaker:
    instances:
      minio:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
      rabbitmq:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
  bulkhead:
    instances:
      upload:
        max-concurrent-calls: 50
```

### 2.2 Code Changes Required

| File | Change |
|------|--------|
| `pom.xml` | Add `resilience4j-spring-boot3` dependency |
| `IngestController` | Add `@Bulkhead` and `@CircuitBreaker` annotations |
| `MessagePublisherService` | Add publisher confirms callback |
| `MinioStorageService` | Add timeout and circuit breaker |

---

## 3. Next Steps - Project Roadmap

### Phase 1: Production Hardening (Priority: High)
- [ ] **Backpressure**: Implement all items from section 2
- [ ] **API Key Authentication**: Validate X-API-KEY against database
- [ ] **Logging Correlation**: Add traceId/spanId across services
- [ ] **Error Handling**: Implement Dead Letter Queue processing
- [ ] **Health Checks**: Add custom health indicators

### Phase 2: Feature Completion (Priority: Medium)
- [ ] **Validation Service**: Java service for master data validation
- [ ] **Excel Processor**: Add Excel/CSV processor to ETL Engine
- [ ] **Word Processor**: Add Word document processor
- [ ] **Unzip Handler**: Handle ZIP files in Executor Service
- [ ] **Job Status API**: Query job processing status

### Phase 3: Observability (Priority: Medium)
- [ ] **Distributed Tracing**: OpenTelemetry integration
- [ ] **Metrics Dashboard**: Grafana dashboards for Prometheus
- [ ] **Log Aggregation**: ELK or Loki stack
- [ ] **Alerting**: Alert rules for failures

### Phase 4: Scalability (Priority: Low)
- [ ] **Kubernetes Manifests**: K8s deployment configs
- [ ] **Auto-scaling**: HPA for ETL workers
- [ ] **Kafka Partitioning**: Optimize partition strategy
- [ ] **Database Partitioning**: Time-based partitioning for extracted_data

### Phase 5: Security (Priority: Medium)
- [ ] **mTLS**: Mutual TLS between services
- [ ] **Secrets Management**: Vault integration
- [ ] **Input Validation**: File content validation (antivirus scan)
- [ ] **Audit Logging**: Comprehensive audit trail

---

## 4. Immediate Action Items

1. **Add Resilience4j** to `data-integration-service`
2. **Configure file size limits** in `application.yml`
3. **Implement publisher confirms** for RabbitMQ
4. **Add API Key validation** service
5. **Create Job Status API** endpoint

---

## Timeline Estimate

| Phase | Duration | Dependencies |
|-------|----------|--------------|
| Phase 1 | 1-2 weeks | None |
| Phase 2 | 2-3 weeks | Phase 1 |
| Phase 3 | 1-2 weeks | Phase 1 |
| Phase 4 | 2-3 weeks | Phase 2, 3 |
| Phase 5 | 1-2 weeks | Phase 1 |
