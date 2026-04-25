import psycopg2
import json
import uuid
import random
from datetime import datetime, timedelta

# DB connection
conn = psycopg2.connect(
    dbname="recipeapp",
    user="postgres",
    password="password",
    host="localhost",
    port=5432
)
cur = conn.cursor()

# Your test user
USER_ID = "0438a488-c061-7071-57a6-e705a5d4d16f"

# Seed tag — query DELETE FROM recipes WHERE name LIKE 'SEED_%' to remove all seeded recipes
SEED_PREFIX = "SEED_"

# Recipe templates to generate variety
RECIPE_TEMPLATES = [
    {
        "name": "Pasta",
        "descriptions": [
            "A classic Italian pasta dish with rich tomato sauce",
            "Creamy pasta with garlic and herbs",
            "Spicy arrabbiata pasta with fresh basil",
        ],
        "ingredients": [
            [{"name": "spaghetti", "quantity": 200.0, "unit": "g"},
             {"name": "tomato sauce", "quantity": 150.0, "unit": "ml"},
             {"name": "garlic", "quantity": 3.0, "unit": "cloves"},
             {"name": "olive oil", "quantity": 2.0, "unit": "tbsp"}],
            [{"name": "penne", "quantity": 200.0, "unit": "g"},
             {"name": "heavy cream", "quantity": 100.0, "unit": "ml"},
             {"name": "parmesan", "quantity": 50.0, "unit": "g"},
             {"name": "black pepper", "quantity": 1.0, "unit": "tsp"}],
        ]
    },
    {
        "name": "Chicken",
        "descriptions": [
            "Juicy grilled chicken with lemon and herbs",
            "Crispy baked chicken thighs with garlic butter",
            "Tender chicken stir fry with vegetables",
        ],
        "ingredients": [
            [{"name": "chicken breast", "quantity": 300.0, "unit": "g"},
             {"name": "lemon", "quantity": 1.0, "unit": None},
             {"name": "rosemary", "quantity": 2.0, "unit": "tsp"},
             {"name": "olive oil", "quantity": 2.0, "unit": "tbsp"}],
            [{"name": "chicken thighs", "quantity": 400.0, "unit": "g"},
             {"name": "garlic", "quantity": 4.0, "unit": "cloves"},
             {"name": "butter", "quantity": 30.0, "unit": "g"},
             {"name": "thyme", "quantity": 1.0, "unit": "tsp"}],
        ]
    },
    {
        "name": "Salad",
        "descriptions": [
            "Fresh garden salad with vinaigrette dressing",
            "Caesar salad with homemade croutons",
            "Greek salad with feta and olives",
        ],
        "ingredients": [
            [{"name": "romaine lettuce", "quantity": 150.0, "unit": "g"},
             {"name": "cherry tomatoes", "quantity": 100.0, "unit": "g"},
             {"name": "cucumber", "quantity": 1.0, "unit": None},
             {"name": "olive oil", "quantity": 2.0, "unit": "tbsp"}],
            [{"name": "cos lettuce", "quantity": 200.0, "unit": "g"},
             {"name": "parmesan", "quantity": 40.0, "unit": "g"},
             {"name": "croutons", "quantity": 50.0, "unit": "g"},
             {"name": "caesar dressing", "quantity": 3.0, "unit": "tbsp"}],
        ]
    },
    {
        "name": "Soup",
        "descriptions": [
            "Hearty vegetable soup with crusty bread",
            "Creamy tomato soup with basil",
            "Chicken noodle soup for the soul",
        ],
        "ingredients": [
            [{"name": "mixed vegetables", "quantity": 300.0, "unit": "g"},
             {"name": "vegetable stock", "quantity": 500.0, "unit": "ml"},
             {"name": "onion", "quantity": 1.0, "unit": None},
             {"name": "garlic", "quantity": 2.0, "unit": "cloves"}],
            [{"name": "tomatoes", "quantity": 400.0, "unit": "g"},
             {"name": "heavy cream", "quantity": 100.0, "unit": "ml"},
             {"name": "basil", "quantity": 10.0, "unit": "g"},
             {"name": "chicken stock", "quantity": 300.0, "unit": "ml"}],
        ]
    },
    {
        "name": "Rice",
        "descriptions": [
            "Fluffy basmati rice with fragrant spices",
            "Fried rice with egg and vegetables",
            "Coconut rice with fresh herbs",
        ],
        "ingredients": [
            [{"name": "basmati rice", "quantity": 200.0, "unit": "g"},
             {"name": "cumin", "quantity": 1.0, "unit": "tsp"},
             {"name": "cardamom", "quantity": 2.0, "unit": "pods"},
             {"name": "butter", "quantity": 20.0, "unit": "g"}],
            [{"name": "jasmine rice", "quantity": 200.0, "unit": "g"},
             {"name": "eggs", "quantity": 2.0, "unit": None},
             {"name": "soy sauce", "quantity": 2.0, "unit": "tbsp"},
             {"name": "spring onions", "quantity": 3.0, "unit": "stalks"}],
        ]
    },
    {
        "name": "Steak",
        "descriptions": [
            "Pan-seared ribeye with garlic butter",
            "Grilled sirloin with chimichurri sauce",
            "Slow cooked beef with red wine reduction",
        ],
        "ingredients": [
            [{"name": "ribeye steak", "quantity": 300.0, "unit": "g"},
             {"name": "garlic", "quantity": 3.0, "unit": "cloves"},
             {"name": "butter", "quantity": 40.0, "unit": "g"},
             {"name": "thyme", "quantity": 2.0, "unit": "sprigs"}],
            [{"name": "sirloin steak", "quantity": 280.0, "unit": "g"},
             {"name": "parsley", "quantity": 20.0, "unit": "g"},
             {"name": "red wine vinegar", "quantity": 2.0, "unit": "tbsp"},
             {"name": "olive oil", "quantity": 3.0, "unit": "tbsp"}],
        ]
    },
    {
        "name": "Curry",
        "descriptions": [
            "Fragrant chicken tikka masala with naan",
            "Creamy butter chicken with basmati rice",
            "Spicy lamb curry with fresh coriander",
        ],
        "ingredients": [
            [{"name": "chicken breast", "quantity": 400.0, "unit": "g"},
             {"name": "tikka masala paste", "quantity": 3.0, "unit": "tbsp"},
             {"name": "coconut milk", "quantity": 400.0, "unit": "ml"},
             {"name": "onion", "quantity": 1.0, "unit": None}],
            [{"name": "lamb shoulder", "quantity": 500.0, "unit": "g"},
             {"name": "curry powder", "quantity": 2.0, "unit": "tbsp"},
             {"name": "tomatoes", "quantity": 200.0, "unit": "g"},
             {"name": "coriander", "quantity": 15.0, "unit": "g"}],
        ]
    },
    {
        "name": "Omelette",
        "descriptions": [
            "Fluffy French omelette with fresh herbs",
            "Loaded omelette with cheese and vegetables",
            "Spanish tortilla with potato and onion",
        ],
        "ingredients": [
            [{"name": "eggs", "quantity": 3.0, "unit": None},
             {"name": "butter", "quantity": 15.0, "unit": "g"},
             {"name": "chives", "quantity": 5.0, "unit": "g"},
             {"name": "salt", "quantity": 0.5, "unit": "tsp"}],
            [{"name": "eggs", "quantity": 4.0, "unit": None},
             {"name": "cheddar", "quantity": 50.0, "unit": "g"},
             {"name": "bell pepper", "quantity": 1.0, "unit": None},
             {"name": "mushrooms", "quantity": 80.0, "unit": "g"}],
        ]
    },
    {
        "name": "Tacos",
        "descriptions": [
            "Crispy beef tacos with fresh salsa",
            "Grilled fish tacos with lime crema",
            "Pulled pork tacos with pickled onions",
        ],
        "ingredients": [
            [{"name": "ground beef", "quantity": 300.0, "unit": "g"},
             {"name": "taco shells", "quantity": 8.0, "unit": None},
             {"name": "salsa", "quantity": 100.0, "unit": "g"},
             {"name": "cheddar", "quantity": 50.0, "unit": "g"}],
            [{"name": "white fish fillet", "quantity": 300.0, "unit": "g"},
             {"name": "corn tortillas", "quantity": 6.0, "unit": None},
             {"name": "lime", "quantity": 2.0, "unit": None},
             {"name": "sour cream", "quantity": 50.0, "unit": "ml"}],
        ]
    },
    {
        "name": "Smoothie",
        "descriptions": [
            "Tropical mango and pineapple smoothie",
            "Green detox smoothie with spinach and banana",
            "Berry blast smoothie with yogurt",
        ],
        "ingredients": [
            [{"name": "mango", "quantity": 150.0, "unit": "g"},
             {"name": "pineapple", "quantity": 100.0, "unit": "g"},
             {"name": "coconut milk", "quantity": 200.0, "unit": "ml"},
             {"name": "ice", "quantity": 100.0, "unit": "g"}],
            [{"name": "spinach", "quantity": 50.0, "unit": "g"},
             {"name": "banana", "quantity": 1.0, "unit": None},
             {"name": "almond milk", "quantity": 250.0, "unit": "ml"},
             {"name": "chia seeds", "quantity": 1.0, "unit": "tbsp"}],
        ]
    },
]

def generate_recipes(count=1000):
    recipes = []
    base_date = datetime(2024, 1, 1)

    for i in range(count):
        template = RECIPE_TEMPLATES[i % len(RECIPE_TEMPLATES)]
        desc = template["descriptions"][i % len(template["descriptions"])]
        ingredients = template["ingredients"][i % len(template["ingredients"])]

        # Add slight variation to ingredient quantities so recipes aren't identical
        varied_ingredients = []
        for ing in ingredients:
            varied = ing.copy()
            if ing["quantity"]:
                varied["quantity"] = round(ing["quantity"] * random.uniform(0.8, 1.2), 1)
            varied_ingredients.append(varied)

        recipe = {
            "id": str(uuid.uuid4()),
            "user_id": USER_ID,
            "name": f"{SEED_PREFIX}{template['name']}_{i+1}",
            "raw_input": f"Seeded recipe {i+1} for cache benchmark testing",
            "servings": random.randint(1, 6),
            "description": desc,
            "ingredients": json.dumps(varied_ingredients),
            "image_index": random.randint(0, 17),
            "created_at": base_date + timedelta(hours=i)
        }
        recipes.append(recipe)

    return recipes

def seed():
    print("Generating 1000 recipes...")
    recipes = generate_recipes(1000)

    print("Inserting into database...")
    insert_query = """
        INSERT INTO recipes (id, user_id, name, raw_input, servings, description, ingredients, image_index, created_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
    """

    batch = [(
        r["id"], r["user_id"], r["name"], r["raw_input"],
        r["servings"], r["description"], r["ingredients"],
        r["image_index"], r["created_at"]
    ) for r in recipes]

    cur.executemany(insert_query, batch)
    conn.commit()

    print(f"Done. Inserted {len(recipes)} recipes.")
    print(f"\nTo delete seeded recipes later, run:")
    print(f"  DELETE FROM recipes WHERE name LIKE 'SEED_%';")

def cleanup_preview():
    cur.execute("SELECT COUNT(*) FROM recipes WHERE name LIKE 'SEED_%'")
    count = cur.fetchone()[0]
    print(f"Seeded recipes currently in DB: {count}")

if __name__ == "__main__":
    seed()
    cleanup_preview()
    cur.close()
    conn.close()
