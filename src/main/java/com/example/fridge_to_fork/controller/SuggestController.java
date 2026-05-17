package com.example.fridge_to_fork.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.fridge_to_fork.model.Recipe;
import com.example.fridge_to_fork.model.SuggestionRequest;
import com.example.fridge_to_fork.model.SuggestionResult;
import com.example.fridge_to_fork.repository.RecipeRepository;
import com.example.fridge_to_fork.service.EmbeddingService;
import com.example.fridge_to_fork.service.NewRecipeSuggestionService;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Slf4j
@RestController
@RequestMapping("/api/suggest")
public class SuggestController {

    private final EmbeddingService embeddingService;
    private final RecipeRepository recipeRepository;
    private final NewRecipeSuggestionService newRecipeSuggestionService;

    public SuggestController(EmbeddingService embeddingService, RecipeRepository recipeRepository,
            NewRecipeSuggestionService newRecipeSuggestionService) {
        this.embeddingService = embeddingService;
        this.recipeRepository = recipeRepository;
        this.newRecipeSuggestionService = newRecipeSuggestionService;
    }

    @PostMapping
    public ResponseEntity<SuggestionResult> suggest(@RequestBody SuggestionRequest request, HttpServletRequest httpRequest) {

        String userId = (String) httpRequest.getAttribute("userId");

        String cleanInput = request.getAvailableIngredients().toLowerCase().trim();

        String expandedQuery = "Ingredients: " + cleanInput + ". A recipe that has " + cleanInput;

        float[] queryVector = embeddingService.getEmbedding(expandedQuery);
        String vectorString = embeddingService.toVectorString(queryVector);

        List<Recipe> matches = recipeRepository.findSimilarRecipes(userId, vectorString, 0.70);
        log.debug("Similarity search for user {} returned {} match(es)", userId, matches.size());

        String newRecipe = newRecipeSuggestionService.suggestNewRecipe(request.getAvailableIngredients(), matches);

        return ResponseEntity.ok(new SuggestionResult(matches, newRecipe));
    }


    @PostMapping("/suggestBorrowedRecipes")
    public ResponseEntity<SuggestionResult> suggestBorrowedRecipes(@RequestBody SuggestionRequest request, HttpServletRequest httpRequest) {

        String userId = (String) httpRequest.getAttribute("userId");

        String cleanInput = request.getAvailableIngredients().toLowerCase().trim();

        String expandedQuery = "Ingredients: " + cleanInput + ". A recipe that has " + cleanInput;

        float[] queryVector = embeddingService.getEmbedding(expandedQuery);
        String vectorString = embeddingService.toVectorString(queryVector);

        List<Recipe> matches = recipeRepository.findSimilarRecipesOtherUsers(userId, vectorString, 0.6);
        log.debug("Community similarity search for user {} returned {} match(es)", userId, matches.size());

        return ResponseEntity.ok(new SuggestionResult(matches, null));
    }
}
