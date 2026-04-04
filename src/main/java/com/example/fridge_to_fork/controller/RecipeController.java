package com.example.fridge_to_fork.controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.fridge_to_fork.model.Ingredient;
import com.example.fridge_to_fork.model.Recipe;
import com.example.fridge_to_fork.repository.RecipeRepository;
import com.example.fridge_to_fork.service.EmbeddingService;
import com.example.fridge_to_fork.service.RecipeParsingService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Random;



@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeRepository recipeRepository;
    private final RecipeParsingService recipeParsingService;
    private final EmbeddingService embeddingService;

    public RecipeController(RecipeRepository recipeRepository, RecipeParsingService recipeParsingService,
            EmbeddingService embeddingService) {
        this.recipeRepository = recipeRepository;
        this.recipeParsingService = recipeParsingService;
        this.embeddingService = embeddingService;
    }

    @PostMapping
    public ResponseEntity<Recipe> createRecipe(@RequestBody Recipe recipe, HttpServletRequest request) {

        Random rand = new Random();

        try {
            String userId = (String) request.getAttribute("userId");
            recipe.setUserId(userId);

            recipe.setImageIndex(rand.nextInt(18));
            Recipe saved = recipeRepository.save(recipe);

            List<Ingredient> ingredients = recipe.getIngredients();

            int limit = Math.min(ingredients.size(), 5);
            List<String> essentialNames = ingredients.subList(0, limit).stream()
                    .map(ing -> ing.getName().toLowerCase().trim())
                    .collect(Collectors.toList());

            String essentialList = String.join(", ", essentialNames);

            String weightedIngredients = essentialList + ", " + essentialList;

            String embeddingText = "Recipe: " + recipe.getName().toLowerCase().trim() + " | Essentials: "
                    + weightedIngredients;
            float[] vector = embeddingService.getEmbedding(embeddingText);

            recipeRepository.updateEmbedding(saved.getId().toString(), embeddingService.toVectorString(vector));

            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            throw new RuntimeException("Failed to save recipe: " + e.getMessage(), e);
        }
    }

    @PostMapping(value = "/draft", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> draftRecipe(@RequestBody String rawInput) {

        String jsonDraft = recipeParsingService.parseRecipeWithLLM(rawInput);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonDraft);
    }

    @GetMapping
    public List<Recipe> getMyRecipes(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return recipeRepository.findByUserId(userId);
    }

    @GetMapping("/search")
    public List<Recipe> searchRecipes(@RequestParam String name, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return recipeRepository.findByUserIdAndNameContaining(userId, name);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecipe(@PathVariable UUID id, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        recipeRepository.deleteByIdAndUserId(id, userId);
        return ResponseEntity.noContent().build();
    }
}