package com.example.fridge_to_fork.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class EventSerializationTest {
    
    private JsonSerializer<Object> serializer;
    private ObjectMapper mapper;

    @BeforeEach 
    void setUp() {
        serializer = new JsonSerializer<>();
        mapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule());
    }

    @Test 
    void recipeSavedSerializesWithMetadata() throws Exception {
        RecipeSaved event = RecipeSaved.of("user-1", UUID.randomUUID(), List.of("potatoes", "milk", "butter", "garlic"));

        byte[] bytes = serializer.serialize("test-topic", event);

        JsonNode node = mapper.readTree(bytes);
        assertEquals("RecipeSaved", node.get("eventType").asText());
        assertEquals(1, node.get("schemaVersion").asInt());
        assertEquals("user-1", node.get("userId").asText());
        assertTrue(node.get("recipeId").isTextual());
        assertEquals(4, node.get("ingredients").size());
        assertTrue(node.get("timestamp").isTextual(), "timestamp should be an ISO-8601 string");
    }

    @Test 
    void recipeSavedRoundTrips() throws Exception {
        RecipeSaved original = RecipeSaved.of("user-1", UUID.randomUUID(), List.of("potatoes", "milk", "butter", "garlic"));

        byte[] bytes = serializer.serialize("test-topic", original);
        RecipeSaved back = mapper.readValue(bytes, RecipeSaved.class);

        assertEquals(original, back);

    }

    @Test 
    void searchPerformedSerializedWithMetadata() throws Exception {
        SearchPerformed event = SearchPerformed.of("user-1", List.of("potatoes", "milk", "butter"), 0);

        byte[] bytes = serializer.serialize("test-topic", event);
        JsonNode node = mapper.readTree(bytes);
        assertEquals("SearchPerformed", node.get("eventType").asText());
        assertEquals(1, node.get("schemaVersion").asInt());
        assertEquals("user-1", node.get("userId").asText());
        assertEquals(3, node.get("ingredients").size());
        assertTrue(node.has("matchCount"), "matchCount must be present in JSON");
        assertTrue(node.get("matchCount").isInt(), "matchCount must be a number");
        assertEquals(0, node.get("matchCount").asInt());
        assertTrue(node.get("timestamp").isTextual(), "timestamp should be an ISO-8601 string");
    }


    @Test 
    void searchPerformedRoundTrips() throws Exception{

        SearchPerformed original = SearchPerformed.of("user-1", List.of("potatoes", "milk", "butter"), 0);

        byte[] bytes = serializer.serialize("test-topic", original);
        SearchPerformed back = mapper.readValue(bytes, SearchPerformed.class);
        assertEquals(original, back);
    }


    @Test 
    void recipeDeletedSerializedWithMetadata() throws Exception {
        RecipeDeleted event = RecipeDeleted.of("user-1", UUID.randomUUID());

        byte[] bytes = serializer.serialize("test-topic", event);

        JsonNode node = mapper.readTree(bytes);
        assertEquals("RecipeDeleted", node.get("eventType").asText());
        assertEquals(1, node.get("schemaVersion").asInt());
        assertEquals("user-1", node.get("userId").asText());
        assertTrue(node.get("recipeId").isTextual());
        assertTrue(node.get("timestamp").isTextual(), "timestamp should be an ISO-8601 string");
    }


    @Test 
    void recipeDeletedRoundTrips() throws Exception{

        RecipeDeleted original = RecipeDeleted.of("user-1", UUID.randomUUID());

        byte[] bytes = serializer.serialize("test-topic", original);

        RecipeDeleted back = mapper.readValue(bytes, RecipeDeleted.class);
        assertEquals(original, back);
    }
}
