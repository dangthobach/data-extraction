"""
ETL Engine Configuration
"""
from pydantic_settings import BaseSettings
from pydantic import Field


class Settings(BaseSettings):
    """Application settings loaded from environment variables"""
    
    # Kafka
    kafka_bootstrap_servers: str = Field(default="localhost:9092")
    kafka_consumer_group: str = Field(default="etl_processing_group")
    kafka_topic_file_ready: str = Field(default="etl.file.ready")
    kafka_auto_offset_reset: str = Field(default="earliest")
    
    # MinIO
    minio_endpoint: str = Field(default="localhost:9000")
    minio_access_key: str = Field(default="minioadmin")
    minio_secret_key: str = Field(default="minioadmin")
    minio_secure: bool = Field(default=False)
    
    # PostgreSQL
    postgres_host: str = Field(default="localhost")
    postgres_port: int = Field(default=5432)
    postgres_db: str = Field(default="data_extraction")
    postgres_user: str = Field(default="admin")
    postgres_password: str = Field(default="changeme")
    
    # ETL Settings
    etl_workers: int = Field(default=4)
    batch_size: int = Field(default=10)
    
    @property
    def database_url(self) -> str:
        return f"postgresql://{self.postgres_user}:{self.postgres_password}@{self.postgres_host}:{self.postgres_port}/{self.postgres_db}"
    
    class Config:
        env_file = ".env"
        case_sensitive = False


settings = Settings()
