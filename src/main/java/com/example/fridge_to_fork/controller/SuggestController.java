package com.example.fridge_to_fork.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.fridge_to_fork.event.SearchPerformed;
import com.example.fridge_to_fork.messaging.EventPublisher;
import com.example.fridge_to_fork.model.Recipe;
import com.example.fridge_to_fork.model.SuggestionRequest;
import com.example.fridge_to_fork.model.SuggestionResult;
import com.example.fridge_to_fork.repository.RecipeRepository;
import com.example.fridge_to_fork.service.EmbeddingService;
import com.example.fridge_to_fork.service.NewRecipeSuggestionService;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
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
    private final EventPublisher publisher;

    public SuggestController(EmbeddingService embeddingService, RecipeRepository recipeRepository,
            NewRecipeSuggestionService newRecipeSuggestionService,
            EventPublisher publisher) {
        this.embeddingService = embeddingService;
        this.recipeRepository = recipeRepository;
        this.newRecipeSuggestionService = newRecipeSuggestionService;
        this.publisher = publisher;
    }

    @PostMapping
    public ResponseEntity<SuggestionResult> suggest(@RequestBody SuggestionRequest request, HttpServletRequest httpRequest) {

        String userId = (String) httpRequest.getAttribute("userId");

        String cleanInput = request.getAvailableIngredients().toLowerCase().trim();

        String expandedQuery = "Ingredients: " + cleanInput + ". A recipe that has " + cleanInput;

        float[] queryVector = embeddingService.getEmbedding(expandedQuery);
        String vectorString = embeddingService.toVectorString(queryVector);

        List<String> cleanedIngredientList = Arrays.stream(cleanInput.split(","))
                            .map(s -> s.toLowerCase().trim())
                            .filter(s -> !s.isEmpty())
                            .distinct()
                            .toList();


        List<Recipe> matches = recipeRepository.findSimilarRecipes(userId, vectorString, 0.70);
        publisher.publish(SearchPerformed.of(userId, cleanedIngredientList, matches.size()));

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
