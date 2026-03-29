package com.example.fridge_to_fork;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SuggestionResult {
    private List<Recipe> matches;
    private String newRecipeSuggestion;
}

