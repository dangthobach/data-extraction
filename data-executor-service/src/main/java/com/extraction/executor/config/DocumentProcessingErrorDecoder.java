package com.extraction.executor.config;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Custom error decoder for Document Processing API errors
 */
@Slf4j
public class DocumentProcessingErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultErrorDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        String requestUrl = response.request().url();
        int status = response.status();

        String errorMessage = extractErrorMessage(response);

        log.error("Document Processing API error - Method: {}, URL: {}, Status: {}, Message: {}",
                methodKey, requestUrl, status, errorMessage);

        return switch (status) {
            case 400 -> new DocumentProcessingBadRequestException(
                    "Bad request to Document Processing API: " + errorMessage);
            case 404 -> new DocumentProcessingNotFoundException(
                    "Resource not found in Document Processing API: " + errorMessage);
            case 422 -> new DocumentProcessingValidationException(
                    "Validation error in Document Processing API: " + errorMessage);
            case 500 -> new DocumentProcessingServerException(
                    "Internal server error in Document Processing API: " + errorMessage);
            case 503 -> new DocumentProcessingUnavailableException(
                    "Document Processing API is temporarily unavailable: " + errorMessage);
            default -> defaultErrorDecoder.decode(methodKey, response);
        };
    }

    /**
     * Extract error message from response body
     */
    private String extractErrorMessage(Response response) {
        if (response.body() == null) {
            return "No error message available";
        }

        try {
            String body = new String(response.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return body.isEmpty() ? "Empty error response" : body;
        } catch (IOException e) {
            log.warn("Failed to read error response body", e);
            return "Failed to read error message";
        }
    }

    // Custom exception classes

    public static class DocumentProcessingException extends RuntimeException {
        public DocumentProcessingException(String message) {
            super(message);
        }
    }

    public static class DocumentProcessingBadRequestException extends DocumentProcessingException {
        public DocumentProcessingBadRequestException(String message) {
            super(message);
        }
    }

    public static class DocumentProcessingNotFoundException extends DocumentProcessingException {
        public DocumentProcessingNotFoundException(String message) {
            super(message);
        }
    }

    public static class DocumentProcessingValidationException extends DocumentProcessingException {
        public DocumentProcessingValidationException(String message) {
            super(message);
        }
    }

    public static class DocumentProcessingServerException extends DocumentProcessingException {
        public DocumentProcessingServerException(String message) {
            super(message);
        }
    }

    public static class DocumentProcessingUnavailableException extends DocumentProcessingException {
        public DocumentProcessingUnavailableException(String message) {
            super(message);
        }
    }
}
