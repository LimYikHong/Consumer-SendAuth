package com.worldline.mock.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;

/**
 * Kafka configuration: topic auto-creation and consumer concurrency.
 */
@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topic.batch-request}")
    private String batchRequestTopic;

    @Value("${app.kafka.topic.batch-response}")
    private String batchResponseTopic;

    @Value("${app.kafka.concurrency:3}")
    private int concurrency;

    @Bean
    public NewTopic batchRequestTopic() {
        return TopicBuilder.name(batchRequestTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic batchResponseTopic() {
        return TopicBuilder.name(batchResponseTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory
                = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(concurrency);
        return factory;
    }
}
