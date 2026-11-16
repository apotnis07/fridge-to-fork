package com.example.fridge_to_fork.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    
    @Bean
    public NewTopic recipeTopic(@Value("${app.kafka.topic.recipe-events}") String recipeEventsTopic) {
        return TopicBuilder.name(recipeEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic searchTopic(@Value("${app.kafka.topic.search-events}") String searchEventsTopic) {
        return TopicBuilder.name(searchEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
