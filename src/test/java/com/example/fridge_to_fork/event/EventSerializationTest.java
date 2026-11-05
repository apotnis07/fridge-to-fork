package com.example.fridge_to_fork.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class EventSerializationTest {
    
    private ObjectMapper mapper;

    @BeforeEach 
    void setUp() {
        mapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test 
    void recipeSavedSerializesWithMetadata() throws Exception {
        RecipeSaved event = RecipeSaved.of("user-1", UUID.randomUUID(), List.of("potatoes", "milk", "butter", "garlic"));

        String json = mapper.writeValueAsString(event);

        JsonNode node = mapper.readTree(json);
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

        String json = mapper.writeValueAsString(original);
        RecipeSaved back = mapper.readValue(json, RecipeSaved.class);

        assertEquals(original, back);

    }

    @Test 
    void searchPerformedSerializedWithMetadata() throws Exception {
        SearchPerformed event = SearchPerformed.of("user-1", List.of("potatoes", "milk", "butter"), 0);

        String json = mapper.writeValueAsString(event);

        JsonNode node = mapper.readTree(json);
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

        String json = mapper.writeValueAsString(original);
        SearchPerformed back = mapper.readValue(json, SearchPerformed.class);
        assertEquals(original, back);
    }


    @Test 
    void recipeDeletedSerializedWithMetadata() throws Exception {
        RecipeDeleted event = RecipeDeleted.of("user-1", UUID.randomUUID());

        String json = mapper.writeValueAsString(event);

        JsonNode node = mapper.readTree(json);
        assertEquals("RecipeDeleted", node.get("eventType").asText());
        assertEquals(1, node.get("schemaVersion").asInt());
        assertEquals("user-1", node.get("userId").asText());
        assertTrue(node.get("recipeId").isTextual());
        assertTrue(node.get("timestamp").isTextual(), "timestamp should be an ISO-8601 string");
    }


    @Test 
    void recipeDeletedRoundTrips() throws Exception{

        RecipeDeleted original = RecipeDeleted.of("user-1", UUID.randomUUID());

        String json = mapper.writeValueAsString(original);
        RecipeDeleted back = mapper.readValue(json, RecipeDeleted.class);
        assertEquals(original, back);
    }
}
