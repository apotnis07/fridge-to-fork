package com.example.fridge_to_fork.service;

import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import com.example.fridge_to_fork.model.Ingredient;
import com.example.fridge_to_fork.model.Recipe;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

@Service
public class NewRecipeSuggestionService {

    private final BedrockRuntimeClient client;

    public NewRecipeSuggestionService(BedrockRuntimeClient client) {
        this.client = client;
    }

    public String suggestNewRecipe(String availableIngredients, List<Recipe> matchedRecipes) {

        String matchedRecipeContext = matchedRecipes.isEmpty()
                ? "No saved recipes found."
                : matchedRecipes.stream()
                        .map(r -> "- " + r.getName() + ": " +
                                r.getIngredients().stream()
                                        .map(Ingredient::getName)
                                        .collect(Collectors.joining(", ")))
                        .collect(Collectors.joining("\n"));

            String prompt = """
                You are Remi, a professional and sensible AI chef.
                The user has these ingredients: %s
        
                User's cooking style context: %s
        
                INSTRUCTIONS:
                1. Categorize the ingredients into 'Compatible' and 'Incompatible' groups. 
                2. Select the largest or most high-quality 'Compatible' group (e.g., Savory vs. Sweet).
                3. IGNORE any ingredients that would create a disgusting or physically impossible dish.
                4. Ensure the recipe is a real, edible meal a human would enjoy using ONLY the selected group.
        
                Format your response EXACTLY like this — no deviation:
                Line 1: Recipe name only (e.g. "Garlic Butter Chicken Bowl")
                Line 2: A vivid, appetizing 10-20 word description of the finished dish
                Line 3: Blank line
                Then output exactly two sections with these exact headers:
                INGREDIENTS:
                (one ingredient per line starting with -)
                STEPS:
                (one step per line starting with a number)
        
                Keep ingredients and steps concise, under 120 words total.
                If missing a key ingredient, suggest a substitution inline.
                """.formatted(availableIngredients, matchedRecipeContext);

        JSONObject payload = new JSONObject()
                .put("anthropic_version", "bedrock-2023-05-31")
                .put("max_tokens", 600)
                .put("messages", new JSONArray()
                        .put(new JSONObject()
                                .put("role", "user")
                                .put("content", prompt)));

        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId("us.anthropic.claude-haiku-4-5-20251001-v1:0")
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(payload.toString()))
                .build();

        InvokeModelResponse response = client.invokeModel(request);

        return new JSONObject(response.body().asUtf8String())
                .getJSONArray("content")
                .getJSONObject(0)
                .getString("text");

    }
}
