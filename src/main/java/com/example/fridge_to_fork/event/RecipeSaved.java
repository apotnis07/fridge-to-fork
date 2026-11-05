package com.example.fridge_to_fork.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecipeSaved(
    String eventType,
    int schemaVersion,
    String userId,
    UUID recipeId,
    List<String> ingredients,
    Instant timestamp
) {

    public static final String EVENT_TYPE = "RecipeSaved";
    public static final int SCHEMA_VERSION = 1;

    public static RecipeSaved of(String userId, UUID recipeId, List<String> ingredients) {

        return new RecipeSaved(EVENT_TYPE, SCHEMA_VERSION, userId, recipeId, List.copyOf(ingredients), Instant.now());
    }
}
