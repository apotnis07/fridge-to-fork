package com.example.fridge_to_fork.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import com.example.fridge_to_fork.model.Ingredient;
import com.example.fridge_to_fork.model.Recipe;
import com.example.fridge_to_fork.repository.RecipeRepository;

@Service
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final EmbeddingService embeddingService;

    public RecipeService(RecipeRepository recipeRepository, EmbeddingService embeddingService) {
        this.recipeRepository = recipeRepository;
        this.embeddingService = embeddingService;
    }

    @Cacheable(value = "userRecipes", key = "#userId")
    public List<Recipe> getRecipesForUser(String userId) {
        return recipeRepository.findByUserId(userId);
    }

    @Caching(evict = {
            @CacheEvict(value = "userRecipes", key = "#userId")
    })
    public Recipe createRecipe(Recipe recipe, String userId) {
        recipe.setUserId(userId);

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

        return saved;

    }

    @CacheEvict(value="userRecipes", key="#userId")
    public void deleteRecipe(UUID id, String userId){
        recipeRepository.deleteByIdAndUserId(id, userId);
    }
}
