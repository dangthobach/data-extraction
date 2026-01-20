# Data Extraction Pipeline & Integration Service - Technical Specification

> **Version**: 2.2
> **Updated**: 2026-01-18
> **Architecture**: Hybrid (Python ETL + Java Microservices)

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Tech Stack](#2-tech-stack)
3. [Microservices & Modules](#3-microservices--modules)
4. [Integration Service Design (Detailed)](#4-integration-service-design-detailed)
5. [ETL Pipeline Flow](#5-etl-pipeline-flow)
6. [Data Model & Storage](#6-data-model--storage)
7. [RabbitMQ Configuration](#7-rabbitmq-configuration)

---

## 1. Architecture Overview

### 1.1 High-Level Design

The system leverages a **Hybrid Architecture** to combine the strengths of both ecosystems:
*   **Python**: Used for the **ETL Engine** (File Extraction, Processing, Unzipping, Parsing) leveraging its rich ecosystem (PyMuPDF, Pandas, etc.).
*   **Java (Spring Boot)**: Used for the **Integration Service** (Gateway, Security, Rate Limiting) and **Business Validation**, ensuring high concurrency and enterprise-grade API management.
*   **Communication**: Asynchronous messaging via **RabbitMQ**.

```mermaid
graph TB
    subgraph "External Systems"
        TP[Third-Party Systems]
        SFTP[SFTP Server]
    end

    subgraph "Java Layer (Spring Boot 21)"
        IS[Integration Service (Gateway)]
        IAM[IAM Service (Auth Provider)]
        ES[Executor Service (Ingestion/Orchestration)]
        VS[Validation & Master Service]
    end

    subgraph "Python Layer"
        ETL[ETL Engine (Processing/Extraction)]
    end

    subgraph "Infrastructure"
        RMQ[RabbitMQ]
        KAFKA[Kafka]
        MINIO[(MinIO)]
        DB[(PostgreSQL)]
        REDIS[(Redis - L2 Cache & Rate Limit)]
    end

    TP -->|HTTPS + ClientID/Secret| IS
    IS -->|Check Cache| REDIS
    IS -->|Verify Auth (if miss)| IAM
    IS -->|Publish Request| RMQ
    
    RMQ -->|Consume Job| ES
    ES -->|Pull File| SFTP
    ES -->|Download/Analyze| MINIO
    ES -->|Publish FileReady| KAFKA

    KAFKA -->|Consume FileReady| ETL
    ETL -->|Fetch File| MINIO
    ETL -->|Unzip/Extract| ETL
    ETL -->|Save Data| DB
    ETL -->|Publish Result| RMQ
    
    RMQ -->|Consume Result| VS
    VS -->|Validate & Dedupe| DB
    ```

---

## 2. Tech Stack

### 2.1 General
*   **Message Broker 1 (Ingestion)**: RabbitMQ (Stream & Quorum Queues).
*   **Message Broker 2 (Data Stream)**: Apache Kafka (High Throughput for ETL).
*   **Database**: PostgreSQL 15+.
*   **Cache**: Redis (Cluster/Sentinel) + Caffeine (Local).
*   **Storage**: MinIO.

### 2.2 Java Service Layer (Integration & Biz)
*   **Language**: Java 21 (Virtual Threads).
*   **Framework**: Spring Boot 3.3.x.
*   **Caching**: Spring Cache (Caffeine L1 + Redis L2).
*   **Rate Limiting**: Custom implementation via Redis Lua Scripts or Bucket4j.

### 2.3 Python ETL Layer
*   **Language**: Python 3.11+.
*   **Libs**: PyMuPDF, Pandas, Pydantic, SQLAlchemy.

---

## 3. Microservices & Modules

### 3.1 Integration Service (Java)
**Core Responsibility**: Secure Gateway & Traffic Control.
*   **Defense Layers**:
    *   **L1**: Authentication via **IAM Service** (Cached in Redis/Caffeine).
    *   **L2**: Rate Limiting (100 req/day/system) via Redis.
    *   **L3**: Validation (Basic payload check).
*   **Note**: Does NOT store auth entities. Delegates to IAM.

### 3.2 IAM Service (Java - Spring Boot)
**Core Responsibility**: Centralized Authentication & Authorization.
*   **Entities**: `SystemClient` (ClientId, ClientSecret, Scopes).
*   **Storage**: PostgreSQL (`iam_schema`).
*   **Communication**: Exposes Internal API for Validation.
    *   `POST /internal/auth/validate`: Verifies ClientID/Secret.

### 3.3 Executor Service (Java - Spring Boot)

### 3.2 Executor Service (Java - Spring Boot)
**Core Responsibility**: High-throughput File Ingestion & Orchestration.
*   **Tech**: Java 21, Spring Cloud Stream (Kafka Binder).
*   **Role**:
    1.  **Ingestion**: Connects to SFTP/S3 (Apache COMMONS VFS / Spring Integration).
    2.  **Buffering**: Handles file uploads.
    3.  **Sanitization**: Checks file metadata.
    4.  **Storage**: Puts raw files into MinIO.
    5.  **Notification**: Publishes `file.ready` event to **Kafka Topic**.

### 3.3 ETL Engine (Python Workers)
**Core Responsibility**: CPU-intensive Data Extraction.
*   **Libs**: `confluent-kafka-python` for consuming.
*   **Tasks**: Unzip, Parse PDF, Extract Text/Tables.



---

## 4. Integration Service Design (Detailed)

### 4.1 Security & Rate Limiting Requirements

*   **Authentication**: API Key passed in Header `X-API-KEY`.
*   **Limit**: 100 requests per day per `SystemID`.
*   **Reset Strategy**: Auto-reset at 00:00 UTC (or configured timezone).

### 4.2 Multi-Level Caching Strategy

To minimize latency and reduce Redis round-trips:

| Level | Technology | Content | Expiry | Purpose |
|-------|------------|---------|--------|---------|
| **L1** | **Caffeine** (In-Memory) | API Key Metadata (SystemID, Status) | 10 mins | Zero-latency Auth check. Avoids Redis hit for every request. |
| **L2** | **Redis** | Rate Limit Counters | 24 Hours | Distributed counter for consistency across service instances. |

### 4.3 Request Processing Flow

1.  **Request Arrival**: `POST /api/v1/ingest`.
2.  **Auth Check (L1 Cache)**:
    *   Check Caffeine for `API_KEY`.
    *   *Hit*: Retrieve `SystemID`.
    *   *Miss*: Query Database/Redis (L2) -> Load to Caffeine -> Retrieve `SystemID`.
3.  **Rate Limit Check (L2 Cache - Optimized)**:
    *   Execute **Redis Lua Script** (Atomic operation to reduce round-trips):
        ```lua
        -- Keys: rate_limit:{system_id}:{date}
        -- Args: limit (100)
        local current = redis.call('INCR', KEYS[1])
        if current == 1 then
            redis.call('EXPIRE', KEYS[1], 86400) -- 24h TTL
        end
        if current > tonumber(ARGV[1]) then
            return 0 -- REJECT
        else
            return 1 -- ALLOW
        end
        ```
    *   **Result**:
        *   `0 (REJECT)`: Return `429 Too Many Requests`. Log "Quota Exceeded".
        *   `1 (ALLOW)`: Proceed to publish message.

### 4.4 Reporting & Observability

To track limits and exceeded requests:

1.  **Metric Collection** (Micrometer):
    *   `integration_requests_total{system="A", status="accepted"}`
    *   `integration_requests_total{system="A", status="rate_limited"}`
2.  **Daily Report**:
    *   Scheduled Job (Cron) runs at EOD.
    *   Aggregates metrics or queries specific `usage_log` table (if audit required).
    *   Generates report: "System A: 95/100 used, 0 rejected."

---

## 5. End-to-End Pipeline Flows

### 5.1 Flow 1: Direct File Upload
1.  **Client**: `POST /upload` -> **Integration Service**.
2.  **Integration**:
    *   Streams request -> **RabbitMQ** (`ingest.request`).
3.  **Executor Service (Java)**:
    *   Consumes `ingest.request`.
    *   Uploads to MinIO.
    *   Publishes message -> **Kafka Topic** (`etl.file.ready`).
4.  **ETL Engine (Python)**:
    *   Consumes from **Kafka**.
    *   Processing...

### 5.2 Flow 2: Event Trigger (SFTP Pull)
1.  **Client**: `POST /job/trigger` -> **Integration Service**.
2.  **Integration**:
    *   Publishes `ingest.request` {type: "SYNC"} -> **RabbitMQ**.
3.  **Executor Service (Java)**:
    *   Consumes request.
    *   **Virtual Threads**: Spawns threads to download from SFTP in parallel.
    *   Streams to MinIO.
    *   Publishes events -> **Kafka Topic** (`etl.file.ready`).
4.  **ETL Engine**:
    *   Consumes from **Kafka**.
    *   Processing...

---

## 6. Data Model & Storage

### 6.1 Integration Logs (New)
To support auditing and reporting.

```sql
CREATE TABLE integration_request_logs (
    id UUID PRIMARY KEY,
    request_id VARCHAR(50),
    system_id VARCHAR(50),
    api_key_hash VARCHAR(64),
    status VARCHAR(20),     -- ACCEPTED, RATE_LIMITED, INVALID_AUTH
    client_ip VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 6.2 Master Data (Existing)
*   `extracted_data` (Managed by Python)
*   `master_data` (Managed by Java Validation)

---

## 7. Configuration

### 7.1 RabbitMQ (Integration -> Executor)
*   **Exchange**: `integration.direct`
*   **Queue**: `q.executor.ingest`

### 7.2 Kafka (Executor -> ETL)
*   **Topic**: `etl.file.ready`
*   **Partitions**: 10 (Allow parallel consumption by ETL workers).
*   **Consumer Group**: `etl_processing_group`.


