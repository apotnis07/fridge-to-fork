package com.example.fridge_to_fork.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.fridge_to_fork.model.Recipe;

import jakarta.transaction.Transactional;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, UUID> {

        List<Recipe> findByUserId(String userId);

        List<Recipe> findByUserIdAndNameContaining(String userId, String name);

        Optional<Recipe> findByIdAndUserId(UUID id, String userId);

        @Modifying
        @Transactional
        void deleteByIdAndUserId(UUID id, String userId);

        @Modifying
        @Transactional
        @Query(value = "UPDATE recipes SET embedding = CAST(:embedding AS vector) WHERE id = CAST(:id AS uuid)", nativeQuery = true)
        void updateEmbedding(@Param("id") String id, @Param("embedding") String embedding);

        @Query(value = """
                        SELECT * FROM recipes
                        WHERE user_id = :userId
                        AND (embedding <=> CAST(:embedding AS vector)) < :threshold
                        ORDER BY embedding <=> CAST(:embedding AS vector)
                        LIMIT 5
                        """, nativeQuery = true)
        List<Recipe> findSimilarRecipes(@Param("userId") String userId, @Param("embedding") String embedding,
                        @Param("threshold") double threshold);


        @Query(value = """
                        SELECT * FROM recipes
                        WHERE user_id != :userId
                        AND (embedding <=> CAST(:embedding AS vector)) < :threshold
                        ORDER BY embedding <=> CAST(:embedding AS vector)
                        LIMIT 2
                        """, nativeQuery = true)
        List<Recipe> findSimilarRecipesOtherUsers(@Param("userId") String userId, @Param("embedding") String embedding,
                        @Param("threshold") double threshold);

        @Query(value = """
                        SELECT name, (embedding <=> CAST(:embedding AS vector)) as distance
                        FROM recipes
                        WHERE user_id = :userId
                        ORDER BY distance
                        """, nativeQuery = true)
        List<Object[]> findSimilarRecipesWithScores(
                        @Param("userId") String userId,
                        @Param("embedding") String embedding);


                        @Query(value = """
                        SELECT name, (embedding <=> CAST(:embedding AS vector)) as distance
                        FROM recipes
                        WHERE user_id != :userId
                        ORDER BY distance
                        """, nativeQuery = true)
        List<Object[]> findSimilarRecipesOtherUsersWithScores(
                        @Param("userId") String userId,
                        @Param("embedding") String embedding);

}
