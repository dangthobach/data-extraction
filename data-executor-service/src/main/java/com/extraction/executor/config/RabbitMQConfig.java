package com.extraction.executor.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${messaging.queue.executor-ingest}")
    private String queueName;

    @Bean
    public Queue executorIngestQueue() {
        return QueueBuilder.durable(queueName)
                .withArgument("x-queue-type", "quorum")
                .build();
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    // --- Document Processing Queues ---
    public static final String QUEUE_SPLIT = "split.queue";
    public static final String QUEUE_CHECK = "check.queue";
    public static final String QUEUE_EXTRACT = "extract.queue";
    public static final String QUEUE_CROSSCHECK = "crosscheck.queue";
    public static final String QUEUE_HISTORY_REQUEST = "history.request.queue";
}
