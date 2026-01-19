"""
Processing Router - Routes files to appropriate processors based on type
"""
from typing import Dict, Any
import structlog

from processors.pdf_processor import pdf_processor

logger = structlog.get_logger()


class ProcessorRouter:
    """Routes files to appropriate processors based on file type"""
    
    def __init__(self):
        self.processors = {
            "pdf": pdf_processor,
            "application/pdf": pdf_processor,
        }
    
    def process(self, file_data: bytes, file_name: str, content_type: str = None) -> Dict[str, Any]:
        """
        Process file based on its type
        
        Args:
            file_data: File content as bytes
            file_name: Original file name
            content_type: MIME type of the file
            
        Returns:
            Extracted content as dictionary
        """
        # Determine file type
        file_type = self._determine_file_type(file_name, content_type)
        
        # Get processor
        processor = self.processors.get(file_type.lower())
        
        if processor is None:
            logger.warning("no_processor_found", file_name=file_name, file_type=file_type)
            return {
                "file_name": file_name,
                "file_type": file_type,
                "error": f"No processor available for file type: {file_type}"
            }
        
        # Process file
        return processor.process(file_data, file_name)
    
    def _determine_file_type(self, file_name: str, content_type: str = None) -> str:
        """Determine file type from name or content type"""
        if content_type and content_type in self.processors:
            return content_type
        
        # Get extension
        if "." in file_name:
            ext = file_name.rsplit(".", 1)[-1].lower()
            return ext
        
        return "unknown"


# Singleton instance
processor_router = ProcessorRouter()
