"""
Database repository for extracted data
"""
from sqlalchemy import text
from datetime import datetime
from typing import Dict, Any, Optional
import json
import structlog

from database.connection import get_db_session

logger = structlog.get_logger()


def save_extracted_data(
    job_id: str,
    file_name: str,
    file_type: str,
    content: Dict[str, Any],
    raw_text: Optional[str] = None
) -> str:
    """Save extracted data to database"""
    with get_db_session() as session:
        result = session.execute(
            text("""
                INSERT INTO extracted_data (job_id, file_name, file_type, content_json, raw_text)
                VALUES (:job_id, :file_name, :file_type, :content_json, :raw_text)
                RETURNING id
            """),
            {
                "job_id": job_id,
                "file_name": file_name,
                "file_type": file_type,
                "content_json": json.dumps(content),
                "raw_text": raw_text
            }
        )
        record_id = str(result.fetchone()[0])
        logger.info("extracted_data_saved", job_id=job_id, record_id=record_id)
        return record_id


def update_job_status(job_id: str, status: str, error_message: Optional[str] = None):
    """Update job status"""
    with get_db_session() as session:
        session.execute(
            text("""
                UPDATE file_jobs 
                SET status = :status, error_message = :error_message, updated_at = :updated_at
                WHERE job_id = :job_id
            """),
            {
                "job_id": job_id,
                "status": status,
                "error_message": error_message,
                "updated_at": datetime.utcnow()
            }
        )
        logger.info("job_status_updated", job_id=job_id, status=status)
