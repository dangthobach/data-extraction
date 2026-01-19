package com.extraction.executor.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${kafka.topic.file-ready}")
    private String fileReadyTopic;

    @Bean
    public NewTopic fileReadyTopic() {
        return TopicBuilder.name(fileReadyTopic)
                .partitions(10) // Allow parallel consumption
                .replicas(1) // Change to 3 in production
                .build();
    }
}
