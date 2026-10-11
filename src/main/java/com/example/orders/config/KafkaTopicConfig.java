package com.example.orders.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic orderEventsTopic(AppProperties props) {
        return TopicBuilder.name(props.topics().orderEvents()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentEventsTopic(AppProperties props) {
        return TopicBuilder.name(props.topics().paymentEvents()).partitions(3).replicas(1).build();
    }
}
