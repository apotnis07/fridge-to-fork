package com.example.fridge_to_fork;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/suggest")
public class SuggestController {

    private final EmbeddingService embeddingService;
    private final RecipeRepository recipeRepository;
    private final NewRecipeSuggestionService newRecipeSuggestionService;

    // private static final String MOCK_USER_ID = "temp-user-123";

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

        String expandedQuery = "Ingredients: " + cleanInput + ", " + cleanInput;

        float[] queryVector = embeddingService.getEmbedding(expandedQuery);
        String vectorString = embeddingService.toVectorString(queryVector);

        // Debug — log distances to console
        List<Object[]> scores = recipeRepository.findSimilarRecipesWithScores(userId, vectorString);
        scores.forEach(row -> System.out.println("Recipe: " + row[0] + " | Distance: " + row[1]));

        List<Recipe> matches = recipeRepository.findSimilarRecipes(userId, vectorString, 0.75);

        String newRecipe = newRecipeSuggestionService.suggestNewRecipe(request.getAvailableIngredients(), matches);

        return ResponseEntity.ok(new SuggestionResult(matches, newRecipe));
    }
}
