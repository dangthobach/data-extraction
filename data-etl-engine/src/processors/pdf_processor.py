"""
PDF Processor - Extract text and tables from PDF files
"""
import io
from typing import Dict, Any, List
import fitz  # PyMuPDF
import structlog

logger = structlog.get_logger()


class PdfProcessor:
    """Process PDF files and extract content"""
    
    def process(self, file_data: bytes, file_name: str) -> Dict[str, Any]:
        """
        Extract content from PDF file
        
        Returns:
            Dict containing extracted text, metadata, and any tables found
        """
        logger.info("processing_pdf", file_name=file_name)
        
        try:
            doc = fitz.open(stream=file_data, filetype="pdf")
            
            result = {
                "file_name": file_name,
                "file_type": "PDF",
                "page_count": len(doc),
                "metadata": self._extract_metadata(doc),
                "pages": [],
                "full_text": ""
            }
            
            full_text_parts = []
            
            for page_num, page in enumerate(doc):
                page_data = self._process_page(page, page_num)
                result["pages"].append(page_data)
                full_text_parts.append(page_data["text"])
            
            result["full_text"] = "\n\n".join(full_text_parts)
            
            doc.close()
            logger.info("pdf_processed", file_name=file_name, pages=result["page_count"])
            
            return result
            
        except Exception as e:
            logger.error("pdf_processing_error", file_name=file_name, error=str(e))
            raise
    
    def _extract_metadata(self, doc: fitz.Document) -> Dict[str, Any]:
        """Extract PDF metadata"""
        metadata = doc.metadata or {}
        return {
            "title": metadata.get("title", ""),
            "author": metadata.get("author", ""),
            "subject": metadata.get("subject", ""),
            "creator": metadata.get("creator", ""),
            "producer": metadata.get("producer", ""),
            "creation_date": metadata.get("creationDate", ""),
            "mod_date": metadata.get("modDate", "")
        }
    
    def _process_page(self, page: fitz.Page, page_num: int) -> Dict[str, Any]:
        """Process a single PDF page"""
        text = page.get_text("text")
        
        # Extract text blocks for structure
        blocks = page.get_text("dict")["blocks"]
        text_blocks = []
        
        for block in blocks:
            if block["type"] == 0:  # Text block
                block_text = ""
                for line in block.get("lines", []):
                    for span in line.get("spans", []):
                        block_text += span.get("text", "")
                    block_text += "\n"
                text_blocks.append({
                    "text": block_text.strip(),
                    "bbox": block["bbox"]
                })
        
        return {
            "page_number": page_num + 1,
            "text": text,
            "text_blocks": text_blocks,
            "width": page.rect.width,
            "height": page.rect.height
        }
    
    def extract_tables(self, file_data: bytes) -> List[List[List[str]]]:
        """
        Extract tables from PDF (basic implementation)
        Returns list of tables, each table is a list of rows
        """
        # For more advanced table extraction, consider using tabula-py or camelot
        # This is a placeholder for basic text-based table detection
        return []


# Singleton instance
pdf_processor = PdfProcessor()
