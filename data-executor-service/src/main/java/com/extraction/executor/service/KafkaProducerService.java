package com.extraction.executor.service;

import com.extraction.executor.dto.FileReadyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, FileReadyEvent> kafkaTemplate;

    @Value("${kafka.topic.file-ready}")
    private String fileReadyTopic;

    /**
     * Publish file ready event to Kafka
     * Uses jobId as partition key for ordering
     */
    public void publishFileReady(FileReadyEvent event) {
        log.info("Publishing file ready event: jobId={}, fileId={}, path={}",
                event.getJobId(), event.getFileId(), event.getMinioPath());

        CompletableFuture<SendResult<String, FileReadyEvent>> future = kafkaTemplate.send(fileReadyTopic,
                event.getJobId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish file ready event: jobId={}, error={}",
                        event.getJobId(), ex.getMessage(), ex);
            } else {
                log.debug("Published file ready event: jobId={}, partition={}, offset={}",
                        event.getJobId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
