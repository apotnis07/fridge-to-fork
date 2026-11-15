package com.example.fridge_to_fork.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.fridge_to_fork.event.RecipeDeleted;
import com.example.fridge_to_fork.event.RecipeSaved;
import com.example.fridge_to_fork.model.Ingredient;
import com.example.fridge_to_fork.model.Recipe;
import com.example.fridge_to_fork.repository.RecipeRepository;

@Service
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final EmbeddingService embeddingService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public RecipeService(RecipeRepository recipeRepository, EmbeddingService embeddingService, ApplicationEventPublisher applicationEventPublisher) {
        this.recipeRepository = recipeRepository;
        this.embeddingService = embeddingService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Cacheable(value = "userRecipes", key = "#userId")
    public List<Recipe> getRecipesForUser(String userId) {
        return recipeRepository.findByUserId(userId);
    }

    @Transactional 
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

        List<String> lowerCaseTrimmedIngredientList = ingredients.stream()
                .map(ing -> ing.getName().toLowerCase().trim())
                .collect(Collectors.toList());

        applicationEventPublisher.publishEvent(RecipeSaved.of(userId, saved.getId(), lowerCaseTrimmedIngredientList));
        return saved;

    }

    @Transactional 
    @CacheEvict(value="userRecipes", key="#userId")
    public void deleteRecipe(UUID id, String userId){
        int deleted = recipeRepository.deleteByIdAndUserId(id, userId);
        if (deleted > 0) {
            applicationEventPublisher.publishEvent(RecipeDeleted.of(userId, id));
        }
    }
}
