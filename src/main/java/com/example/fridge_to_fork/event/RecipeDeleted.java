package com.example.fridge_to_fork.event;

import java.time.Instant;
import java.util.UUID;

public record RecipeDeleted(
    String eventType,
    int schemaVersion,
    String userId,
    UUID recipeId,
    Instant timestamp

) {
    public static final String EVENT_TYPE = "RecipeDeleted";
    public static final int SCHEMA_VERSION = 1;
    

    public static RecipeDeleted of(String userId, UUID recipeId) {
        return new RecipeDeleted(EVENT_TYPE, SCHEMA_VERSION, userId, recipeId, Instant.now());
    }
}
