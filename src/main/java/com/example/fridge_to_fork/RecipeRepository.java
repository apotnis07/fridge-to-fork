package com.example.fridge_to_fork;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, UUID> {
    
    List<Recipe> findByUserId(String userId);   

    List<Recipe> findByUserIdAndNameContaining(String userId, String name);
    Optional<Recipe> findByIdAndUserId(UUID id, String userId);
}
