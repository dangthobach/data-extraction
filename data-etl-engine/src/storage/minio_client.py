"""
MinIO Storage Client
"""
import io
from minio import Minio
from minio.error import S3Error
import structlog

from config import settings

logger = structlog.get_logger()


class MinioClient:
    """Client for MinIO object storage operations"""
    
    def __init__(self):
        endpoint = settings.minio_endpoint.replace("http://", "").replace("https://", "")
        self.client = Minio(
            endpoint,
            access_key=settings.minio_access_key,
            secret_key=settings.minio_secret_key,
            secure=settings.minio_secure
        )
        logger.info("minio_client_initialized", endpoint=endpoint)
    
    def download_file(self, bucket: str, object_name: str) -> bytes:
        """Download file from MinIO and return as bytes"""
        try:
            response = self.client.get_object(bucket, object_name)
            data = response.read()
            response.close()
            response.release_conn()
            logger.debug("file_downloaded", bucket=bucket, object_name=object_name, size=len(data))
            return data
        except S3Error as e:
            logger.error("minio_download_error", bucket=bucket, object_name=object_name, error=str(e))
            raise
    
    def download_file_stream(self, bucket: str, object_name: str):
        """Download file from MinIO as stream"""
        try:
            response = self.client.get_object(bucket, object_name)
            return response
        except S3Error as e:
            logger.error("minio_download_error", bucket=bucket, object_name=object_name, error=str(e))
            raise
    
    def get_object_info(self, bucket: str, object_name: str):
        """Get object metadata"""
        try:
            return self.client.stat_object(bucket, object_name)
        except S3Error as e:
            logger.error("minio_stat_error", bucket=bucket, object_name=object_name, error=str(e))
            raise


# Singleton instance
minio_client = MinioClient()
