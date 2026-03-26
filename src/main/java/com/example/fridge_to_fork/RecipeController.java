package com.example.fridge_to_fork;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;



@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RecipeController {
    
    @Autowired
    private RecipeRepository recipeRepository;

    @PostMapping("/add")
    public Recipe addRecipe(@RequestBody Recipe recipe) {
        return recipeRepository.save(recipe);
    }
    

    @GetMapping("/all")
    public List<Recipe> getAllRecipes() {
        return recipeRepository.findAll();
    }
    

    @GetMapping("/search")
    public List<Recipe> getUserRecipe(@RequestParam String userId) {
        return recipeRepository.findByUserId(userId);
    }
    
}
