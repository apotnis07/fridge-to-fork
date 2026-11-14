package com.example.fridge_to_fork.event;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

public record SearchPerformed(
    String eventType,
    int schemaVersion,
    String userId,
    List<String> ingredients,
    int matchCount,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp
) {
   
    public static final String EVENT_TYPE = "SearchPerformed";
    public static final int SCHEMA_VERSION = 1;
    
    public static SearchPerformed of(String userId, List<String> ingredients, int matchCount) {
        return new SearchPerformed(EVENT_TYPE, SCHEMA_VERSION, userId, List.copyOf(ingredients), matchCount, Instant.now());
    }
}
