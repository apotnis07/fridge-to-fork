package com.example.fridge_to_fork;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name="recipes")
@Data
@NoArgsConstructor
public class Recipe {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String name;

    @Column(name = "raw_input", columnDefinition = "TEXT")
    private String rawInput;

    private Integer servings;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Convert(converter = IngredientConverter.class)
    @Column(name = "ingredients", columnDefinition = "text")
    private List<Ingredient> ingredients;

    // @Column(columnDefinition = "TEXT")
    // private String ingredients; // Simple String now!

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist(){
        this.createdAt = LocalDateTime.now();
    }
}
