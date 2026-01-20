.PHONY: up down build logs clean ps restart

# Default target
all: up

# Build all images
build:
	docker-compose build

# Start all services
up:
	docker-compose up -d --build

# Stop all services
down:
	docker-compose down

# Stop and remove volumes
clean:
	docker-compose down -v --remove-orphans

# View logs
logs:
	docker-compose logs -f

# View logs for specific service
logs-%:
	docker-compose logs -f $*

# Show running containers
ps:
	docker-compose ps

# Restart a specific service
restart-%:
	docker-compose restart $*

# ==========================================
# Development helpers
# ==========================================

# Start only infrastructure (no app services)
infra:
	docker-compose up -d postgres redis rabbitmq zookeeper kafka minio

# Stop infrastructure
infra-down:
	docker-compose stop postgres redis rabbitmq zookeeper kafka minio

# Build Java services only
build-java:
	cd data-integration-service && ./mvnw clean package -DskipTests
	cd data-executor-service && ./mvnw clean package -DskipTests
	cd iam-service && mvn clean package -DskipTests

# Build Python ETL only
build-python:
	cd data-etl-engine && pip install -r requirements.txt

# Run integration service locally (for dev)
run-integration:
	cd data-integration-service && ./mvnw spring-boot:run

# Run executor service locally (for dev)
run-executor:
	cd data-executor-service && ./mvnw spring-boot:run

# Run ETL engine locally (for dev)
run-etl:
	cd data-etl-engine && python src/main.py
