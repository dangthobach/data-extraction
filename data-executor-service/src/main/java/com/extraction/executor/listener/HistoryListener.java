package com.extraction.executor.listener;

import com.extraction.executor.config.RabbitMQConfig;
import com.extraction.executor.entity.DocumentProcessingHistory;
import com.extraction.executor.repository.DocumentProcessingHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HistoryListener {

    private final DocumentProcessingHistoryRepository historyRepository;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_HISTORY_REQUEST)
    public List<DocumentProcessingHistory> handleHistoryRequest(String transactionId) {
        log.info("Fetching history for transaction: {}", transactionId);
        // Assuming findByTransactionId exists and returns List
        return historyRepository.findByTransactionIdOrderByCreatedAtDesc(transactionId);
    }
}
