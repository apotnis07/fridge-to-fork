package com.example.fridge_to_fork.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.example.fridge_to_fork.event.RecipeDeleted;
import com.example.fridge_to_fork.event.RecipeSaved;
import com.example.fridge_to_fork.event.SearchPerformed;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String recipeTopic;
    private final String searchTopic;
    private final boolean enabled;


    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topic.recipe-events}") String recipeTopic,
            @Value("${app.kafka.topic.search-events}") String searchTopic,
            @Value("${remi.events.enabled:false}") boolean enabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.recipeTopic = recipeTopic;
        this.searchTopic = searchTopic;
        this.enabled = enabled;

        if(!enabled) log.info("Remi events disabled (remi.events.enabled=false)");
    }

    public void publishRecipeSaved(RecipeSaved event) {

        if (!enabled) return;
        kafkaTemplate.send(recipeTopic, event.userId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for user {}", event.eventType(), event.userId(), ex);
                    } else {
                        var meta = result.getRecordMetadata();
                        log.info("Published {} to {}-{}@{}", event.eventType(), meta.topic(), meta.partition(),
                                meta.offset());
                    }

                });
    }

    public void publishSearchPerformed(SearchPerformed event) {

        if (!enabled) return;
        kafkaTemplate.send(searchTopic, event.userId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for user {}", event.eventType(), event.userId(), ex);
                    } else {
                        var meta = result.getRecordMetadata();
                        log.info("Published {} to {}-{}@{}", event.eventType(), meta.topic(), meta.partition(),
                                meta.offset());
                    }

                });
    }


    public void publishRecipeDeleted(RecipeDeleted event) {

        if (!enabled) return;
        kafkaTemplate.send(recipeTopic, event.userId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for user {}", event.eventType(), event.userId(), ex);
                    } else {
                        var meta = result.getRecordMetadata();
                        log.info("Published {} to {}-{}@{}", event.eventType(), meta.topic(), meta.partition(),
                                meta.offset());
                    }

                });
    }
}
