package com.example.fridge_to_fork;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    private static final String MOCK_USER_ID = "temp-user-123";

    public SuggestController(EmbeddingService embeddingService, RecipeRepository recipeRepository, NewRecipeSuggestionService newRecipeSuggestionService){
        this.embeddingService = embeddingService;
        this.recipeRepository = recipeRepository;
        this.newRecipeSuggestionService = newRecipeSuggestionService;
    }

    @PostMapping
    public ResponseEntity<SuggestionResult> suggest(@RequestBody SuggestionRequest request) {
        
        float[] queryVector = embeddingService.getEmbedding(request.getAvailableIngredients());
        String vectorString = toVectorString(queryVector);

        List<Recipe> matches = recipeRepository.findSimilarRecipes(MOCK_USER_ID, vectorString, 0.6);

        String newRecipe = newRecipeSuggestionService.suggestNewRecipe(request.getAvailableIngredients(), matches);

        return ResponseEntity.ok(new SuggestionResult(matches, newRecipe));
    }
    
    private String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
