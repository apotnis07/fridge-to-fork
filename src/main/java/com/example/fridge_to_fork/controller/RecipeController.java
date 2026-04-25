package com.example.fridge_to_fork.controller;

import com.example.fridge_to_fork.service.RecipeService;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
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

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;


@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;
    private final RecipeParsingService recipeParsingService;

    @Autowired
    private CacheManager cacheManager;


    public RecipeController(RecipeParsingService recipeParsingService, RecipeService recipeService) {
        this.recipeParsingService = recipeParsingService;
        this.recipeService = recipeService;
    }

    @PostMapping
    public ResponseEntity<Recipe> createRecipe(@RequestBody Recipe recipe, HttpServletRequest request) {

        String userId = (String) request.getAttribute("userId");

        try {
            recipe.setImageIndex(new Random().nextInt(18));
            Recipe saved = recipeService.createRecipe(recipe, userId);
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
        return recipeService.getRecipesForUser(userId);
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecipe(@PathVariable UUID id, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        recipeService.deleteRecipe(id, userId);
        return ResponseEntity.noContent().build();
    }

//     @DeleteMapping("/cache/evict")
//     public ResponseEntity<Void> evictCache(HttpServletRequest request) {
//     String userId = (String) request.getAttribute("userId");
//     Cache cache = cacheManager.getCache("userRecipes");
//     if (cache != null) {
//         cache.evict(userId);
//     }
//     return ResponseEntity.noContent().build();
// }
}