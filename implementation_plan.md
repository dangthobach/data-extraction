# Implementation Plan - Data Extraction Pipeline

## Goal Description
Implement a robust project structure for the Data Extraction Pipeline based on `spec.md`. The design focuses on a **Hybrid Architecture** (Java Spring Boot + Python ETL) with a **Monorepo** approach to ensure convenient "build & run once" capabilities using Docker Compose and Makefiles.

## User Review Required
> [!IMPORTANT]
> This plan assumes a **Monorepo** structure is acceptable. It simplifies local development and deployment synchronization.
> I will use **Docker Compose** for the "one-time run" requirement, which orchestrates the Java Service, (Java) Executor Service, Python Workers, and all Infrastructure (DB, Redis, RabbitMQ, Kafka, MinIO).

## Proposed Changes

### 1. Project Structure
Refactor the root directory to separate concerns while keeping them unified for deployment.
```
/
├── docker-compose.yml           # Orchestrates the entire stack
├── Makefile                     # "One-click" build and run commands
├── .env.example                 # Template for environment variables
├── infra/                       # Infrastructure configuration
│   ├── postgres/                # Init scripts
│   ├── rabbitmq/                # Config
│   ├── kafka/                   # Kafka Config
│   └── minio/                   # Setup
├── data-integration-service/    # [JAVA] Spring Boot Gateway
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
├── data-executor-service/       # [JAVA] Ingestion & Orchestration Service
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
└── data-etl-engine/             # [PYTHON] Processing Workers
    ├── Dockerfile
    ├── requirements.txt
    └── src/
```

### 2. Infrastructure (Docker & Environment)
#### [NEW] [docker-compose.yml](file:///c:/Project/data-extraction/docker-compose.yml)
- **Services**:
    - `postgres`, `redis`, `minio`
    - `rabbitmq`: For Integration -> Executor
    - `kafka` + `zookeeper` (or KRaft): For Executor -> ETL
    - `integration-service`: Java
    - `executor-service`: Java
    - `etl-engine`: Python
- **Networks**: `extraction-network`.

#### [NEW] [Makefile](file:///c:/Project/data-extraction/Makefile)
- `make up`: Build and start all services.
- `make down`: Stop everything.
- `make logs`: View logs.
- `make build`: Rebuild images.

### 3. Java Services
#### [NEW] [data-integration-service](file:///c:/Project/data-extraction/data-integration-service/)
- **Tech**: Java 21, Spring Boot 3.3.x.
- **Port**: 8080.
- **Dockerfile**: Multi-stage build (Build with Maven -> Run with JRE).

#### [NEW] [data-executor-service](file:///c:/Project/data-extraction/data-executor-service/)
- **Tech**: Java 21, Spring Boot 3.3.x, Spring Cloud Stream (Kafka).
- **Function**: Listens to RabbitMQ, Downloads files (SFTP/VFS), Uploads to MinIO, Sends events to Kafka.
- **Port**: 8081.

### 4. ETL Engine (Python)
#### [NEW] [data-etl-engine](file:///c:/Project/data-extraction/data-etl-engine/)
- **Technology**: Python 3.11+, `pandas`, `pymupdf`.
- **Libs**: `confluent-kafka` for high-perf consumption.
- **Structure**: Worker processes consuming `etl.file.ready` topic.
- **Dockerfile**: Python slim image, installs dependencies.

## Verification Plan

### Automated Tests
- **Build Check**: Run `docker-compose build` to ensure all Dockerfiles are valid.

### Manual Verification
- **Run Check**: Run `make up` and verify all containers (Service, Worker, DB, Kafka, etc.) are healthy (`docker ps`).
- **Log Check**: Check logs to see if Python and Java services connect to RabbitMQ, Kafka, and DB successfully.
