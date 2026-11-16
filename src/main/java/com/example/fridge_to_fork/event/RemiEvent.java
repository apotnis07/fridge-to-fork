package com.example.fridge_to_fork.event;

public sealed interface RemiEvent permits RecipeSaved, RecipeDeleted, SearchPerformed {
    String userId();
    String eventType();
}
