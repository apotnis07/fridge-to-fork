package com.example.fridge_to_fork;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

// @RestController
// @RequestMapping("/api/recipes")
// public class RecipeController {

//     private final RecipeRepository recipeRepository;

//     public RecipeController(RecipeRepository recipeRepository) {
//         this.recipeRepository = recipeRepository;
//     }

//     @PostMapping
//     public ResponseEntity<Recipe> createRecipe(
//             @RequestBody Recipe recipe,
//             @AuthenticationPrincipal Jwt jwt
//     ) {
//         recipe.setUserId(jwt.getSubject());

//         return ResponseEntity.ok(recipeRepository.save(recipe));
//     }

//     @GetMapping
//     public List<Recipe> getMyRecipes(@AuthenticationPrincipal Jwt jwt) {
//         String userId = jwt.getSubject();
//         return recipeRepository.findByUserId(userId);
//     }

//     @GetMapping("/search")
//     public List<Recipe> searchRecipes(@RequestParam String name,
//             @AuthenticationPrincipal Jwt jwt) {
//         String userId = jwt.getSubject();
//         return recipeRepository.findByUserIdAndNameContaining(userId, name);
//     }
// }

// Temp code change for transition to amplify auth 

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeRepository recipeRepository;
    private final RecipeParsingService recipeParsingService;
    private final EmbeddingService embeddingService;

    // Hardcoded placeholder ID to simulate a logged-in user
    private static final String MOCK_USER_ID = "temp-user-123";

    public RecipeController(RecipeRepository recipeRepository, RecipeParsingService recipeParsingService,
            EmbeddingService embeddingService) {
        this.recipeRepository = recipeRepository;
        this.recipeParsingService = recipeParsingService;
        this.embeddingService = embeddingService;
    }

    @PostMapping
    public ResponseEntity<Recipe> createRecipe(@RequestBody Recipe recipe) {

        try {
            // Step 1 — save recipe without embedding
            recipe.setUserId(MOCK_USER_ID);
            recipe.setImageIndex((int)(Math.random() * 14));
            Recipe saved = recipeRepository.save(recipe);
    
            // Step 2 — generate embedding from name + ingredient names
            String ingredientNames = recipe.getIngredients().stream()
                    .map(Ingredient::getName)
                    .collect(Collectors.joining(", "));
            String embeddingText = recipe.getName() + " a dish made with " + ingredientNames;
            float[] vector = embeddingService.getEmbedding(embeddingText);
    
            // Step 3 — update embedding column via native query
            recipeRepository.updateEmbedding(saved.getId().toString(), toVectorString(vector));
    
            return ResponseEntity.ok(saved);
    
        } catch (Exception e) {
            throw new RuntimeException("Failed to save recipe: " + e.getMessage(), e);
        }
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

    @PostMapping(value = "/draft", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> draftRecipe(@RequestBody String rawInput) {

        String jsonDraft = recipeParsingService.parseRecipeWithLLM(rawInput);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonDraft);
    }

    @GetMapping
    public List<Recipe> getMyRecipes() {
        // Return recipes belonging to our placeholder ID
        return recipeRepository.findByUserId(MOCK_USER_ID);
    }

    @GetMapping("/search")
    public List<Recipe> searchRecipes(@RequestParam String name) {
        return recipeRepository.findByUserIdAndNameContaining(MOCK_USER_ID, name);
    }
}