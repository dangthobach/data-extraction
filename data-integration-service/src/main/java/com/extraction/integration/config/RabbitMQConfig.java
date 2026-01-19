package com.extraction.integration.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RabbitMQConfig {

    @Value("${messaging.exchange.integration}")
    private String exchangeName;

    @Value("${messaging.queue.executor-ingest}")
    private String queueName;

    @Value("${messaging.routing-key.ingest-request}")
    private String routingKey;

    // DLQ Configuration
    private static final String DLQ_SUFFIX = ".dlq";
    private static final String DLX_SUFFIX = ".dlx";

    @Bean
    public DirectExchange integrationExchange() {
        return new DirectExchange(exchangeName, true, false);
    }

    /**
     * Dead Letter Exchange for failed messages
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(exchangeName + DLX_SUFFIX, true, false);
    }

    /**
     * Main queue with DLQ configuration
     * Uses quorum queue for durability
     */
    @Bean
    public Queue executorIngestQueue() {
        return QueueBuilder.durable(queueName)
                .withArgument("x-queue-type", "quorum")
                // Dead Letter configuration
                .withArgument("x-dead-letter-exchange", exchangeName + DLX_SUFFIX)
                .withArgument("x-dead-letter-routing-key", routingKey + DLQ_SUFFIX)
                // Delivery limit for retry (requeue up to 3 times before DLQ)
                .withArgument("x-delivery-limit", 3)
                .build();
    }

    /**
     * Dead Letter Queue to store failed messages
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(queueName + DLQ_SUFFIX)
                .withArgument("x-queue-type", "quorum")
                .build();
    }

    @Bean
    public Binding binding(Queue executorIngestQueue, DirectExchange integrationExchange) {
        return BindingBuilder
                .bind(executorIngestQueue)
                .to(integrationExchange)
                .with(routingKey);
    }

    @Bean
    public Binding dlqBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder
                .bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(routingKey + DLQ_SUFFIX);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);

        // Enable Publisher Confirms (Backpressure)
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("Message confirmed: {}", correlationData != null ? correlationData.getId() : "unknown");
            } else {
                log.error("Message NOT confirmed: {}, cause: {}",
                        correlationData != null ? correlationData.getId() : "unknown", cause);
            }
        });

        // Enable Publisher Returns for unroutable messages
        template.setReturnsCallback(returned -> {
            log.error("Message returned: exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    returned.getReplyCode(),
                    returned.getReplyText());
        });

        template.setMandatory(true);

        return template;
    }
}
