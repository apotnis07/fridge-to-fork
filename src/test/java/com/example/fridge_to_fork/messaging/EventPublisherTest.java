package com.example.fridge_to_fork.messaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import com.example.fridge_to_fork.event.RecipeDeleted;
import com.example.fridge_to_fork.event.RecipeSaved;
import com.example.fridge_to_fork.event.SearchPerformed;

@ExtendWith(MockitoExtension.class)
public class EventPublisherTest {

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void disabledPublisherNeverTouchesKafka() {
        EventPublisher publisher = new EventPublisher(kafkaTemplate, "recipe", "search", false);

        publisher.publish(RecipeSaved.of("user-1", UUID.randomUUID(), List.of("ing1", "ing2")));
        publisher.publish(RecipeDeleted.of("user-1", UUID.randomUUID()));
        publisher.publish(SearchPerformed.of("user-1", List.of("ing1"), 0));

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void enabledPublisherSendsToTopicKeyedByUser() {

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(new CompletableFuture<>());

        EventPublisher publisher = new EventPublisher(kafkaTemplate, "recipe", "search", true);

        RecipeSaved event = RecipeSaved.of("user-1", UUID.randomUUID(), List.of("ing1", "ing2"));
        publisher.publish(event);
        verify(kafkaTemplate).send("recipe", "user-1", event);

        SearchPerformed search = SearchPerformed.of("user-1", List.of("ing1"), 0);
        publisher.publish(search);
        verify(kafkaTemplate).send("search", "user-1", search);

    }

    @Test
    void publishSwallowsSynchronousSendFailure() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("broker unreachable"));

        EventPublisher publisher = new EventPublisher(kafkaTemplate, "recipe", "search", true);

        assertDoesNotThrow(
                () -> publisher.publish(RecipeSaved.of("user-1", UUID.randomUUID(), List.of("ing1", "ing2"))));

    }

    @Test
    void publishSwallowsAsynchronousSendFailure() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("delivery timed out")));

        EventPublisher publisher = new EventPublisher(kafkaTemplate, "recipe", "search", true);

        assertDoesNotThrow(
            () -> publisher.publish(RecipeSaved.of("user-1", UUID.randomUUID(), List.of("ing1", "ing2"))));
    }
}
