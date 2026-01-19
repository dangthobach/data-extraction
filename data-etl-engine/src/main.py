"""
ETL Engine - Main Entry Point

This application consumes file ready events from Kafka,
downloads files from MinIO, processes them, and saves results to PostgreSQL.
"""
import signal
import sys
from typing import Dict, Any
import structlog

# Configure structured logging
structlog.configure(
    processors=[
        structlog.stdlib.filter_by_level,
        structlog.stdlib.add_logger_name,
        structlog.stdlib.add_log_level,
        structlog.stdlib.PositionalArgumentsFormatter(),
        structlog.processors.TimeStamper(fmt="iso"),
        structlog.processors.StackInfoRenderer(),
        structlog.processors.format_exc_info,
        structlog.processors.UnicodeDecoder(),
        structlog.processors.JSONRenderer()
    ],
    wrapper_class=structlog.stdlib.BoundLogger,
    context_class=dict,
    logger_factory=structlog.stdlib.LoggerFactory(),
    cache_logger_on_first_use=True,
)

logger = structlog.get_logger()

from config import settings
from database.connection import test_connection
from database.repository import save_extracted_data, update_job_status
from storage.minio_client import minio_client
from processors.router import processor_router
from messaging.kafka_consumer import create_consumer


def process_file_ready_event(event: Dict[str, Any]):
    """
    Process a file ready event
    
    1. Download file from MinIO
    2. Process file based on type
    3. Save extracted data to database
    """
    job_id = event.get('jobId')
    file_id = event.get('fileId')
    file_name = event.get('fileName')
    bucket = event.get('bucket')
    minio_path = event.get('minioPath')
    content_type = event.get('contentType')
    
    logger.info("processing_file", 
               job_id=job_id, 
               file_id=file_id, 
               file_name=file_name)
    
    try:
        # Extract object name from path
        # Path format: "bucket/object/path"
        if minio_path.startswith(bucket + "/"):
            object_name = minio_path[len(bucket) + 1:]
        else:
            object_name = minio_path
        
        # Download file from MinIO
        file_data = minio_client.download_file(bucket, object_name)
        logger.info("file_downloaded", file_name=file_name, size=len(file_data))
        
        # Process file
        extracted_content = processor_router.process(file_data, file_name, content_type)
        logger.info("file_processed", file_name=file_name)
        
        # Save to database
        raw_text = extracted_content.get('full_text', '')
        record_id = save_extracted_data(
            job_id=job_id,
            file_name=file_name,
            file_type=extracted_content.get('file_type', 'unknown'),
            content=extracted_content,
            raw_text=raw_text[:10000] if raw_text else None  # Truncate if too long
        )
        
        logger.info("extraction_complete", 
                   job_id=job_id, 
                   file_id=file_id, 
                   record_id=record_id)
        
    except Exception as e:
        logger.error("file_processing_failed",
                    job_id=job_id,
                    file_id=file_id,
                    error=str(e))
        try:
            update_job_status(job_id, "FAILED", str(e))
        except:
            pass
        raise


def main():
    """Main entry point"""
    logger.info("etl_engine_starting", 
               kafka_servers=settings.kafka_bootstrap_servers,
               topic=settings.kafka_topic_file_ready)
    
    # Test database connection
    if not test_connection():
        logger.error("database_connection_failed")
        sys.exit(1)
    
    # Create consumer
    consumer = create_consumer()
    
    # Setup graceful shutdown
    def signal_handler(sig, frame):
        logger.info("shutdown_signal_received")
        consumer.stop()
        sys.exit(0)
    
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    # Start consuming
    logger.info("etl_engine_ready")
    consumer.start(process_file_ready_event)


if __name__ == "__main__":
    main()
