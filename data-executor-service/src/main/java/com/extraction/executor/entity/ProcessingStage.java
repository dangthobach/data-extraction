package com.extraction.executor.entity;

/**
 * Enum representing document processing stages
 */
public enum ProcessingStage {
    /**
     * Stage 1: Split and rename documents
     */
    SPLIT_RENAME,

    /**
     * Stage 2: Check document completeness
     */
    CHECK_COMPLETENESS,

    /**
     * Stage 3: Extract structured data
     */
    EXTRACT_DATA,

    /**
     * Stage 4: Cross check consistency
     */
    CROSS_CHECK
}
