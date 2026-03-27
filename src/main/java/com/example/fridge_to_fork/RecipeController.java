package com.example.fridge_to_fork;

import java.util.List;

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

    // Hardcoded placeholder ID to simulate a logged-in user
    private static final String MOCK_USER_ID = "temp-user-123";

    public RecipeController(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    @PostMapping
    public ResponseEntity<Recipe> createRecipe(@RequestBody Recipe recipe) {
        // Use the placeholder instead of jwt.getSubject()
        recipe.setUserId(MOCK_USER_ID);
        return ResponseEntity.ok(recipeRepository.save(recipe));
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