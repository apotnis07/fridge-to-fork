# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Remi** is a full-stack RAG (Retrieval-Augmented Generation) application that lets users save recipes, then find the best match from their personal recipe book given the ingredients they have on hand. It also generates new AI-suggested recipes. The app name in code is `fridge-to-fork`.

## Commands

### Local Development

```bash
# Start PostgreSQL with pgvector via Docker
docker-compose up -d

# Run the Spring Boot app (requires AWS credentials in the terminal session)
./mvnw spring-boot:run

# Build the JAR
./mvnw clean package

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=FridgeToForkApplicationTests
```

The app runs at `http://localhost:8080`. The active profile defaults to `local` (`application-local.properties`).

### Database Setup (first time only)

After `docker-compose up -d`, the `recipes` table is created by Hibernate, but the `embedding` column must be added manually because Hibernate cannot map `vector(1024)`:

```bash
docker exec -it recipe-db psql -U postgres -d recipeapp
CREATE EXTENSION IF NOT EXISTS vector;
ALTER TABLE recipes ADD COLUMN IF NOT EXISTS embedding vector(1024);
\q
```

### AWS Credentials

AWS credentials must be active in the same terminal session running the app. Bedrock calls use `DefaultCredentialsProvider`, so `aws configure` (or an active SSO session) is required.

```bash
aws configure list   # verify credentials
```

## Architecture

### Layers

| Layer | Technology |
|-------|-----------|
| Frontend | Static HTML/CSS/JS (Tailwind) served by Spring Boot from `src/main/resources/static/` |
| Auth (client) | AWS Amplify (`auth.js`) — manages Cognito tokens in the browser |
| Backend | Spring Boot 3.3, Java 21 |
| Database | PostgreSQL 16 + pgvector extension |
| AI — Embeddings | Amazon Titan Embeddings v2 (`amazon.titan-embed-text-v2:0`) — 1024-dim vectors |
| AI — LLM | Claude Haiku 4.5 (`us.anthropic.claude-haiku-4-5-20251001-v1:0`) via AWS Bedrock |

### Request Flow

**Authentication**: Every `/api/*` request is intercepted by `JwtFilter`, which validates the Cognito-issued JWT using the Cognito JWKS URL. The validated Cognito `sub` is stored as the `userId` request attribute and used to scope all DB queries.

**Recipe saving** (`POST /api/recipes`):
1. Client sends confirmed recipe JSON.
2. `RecipeService.createRecipe` saves the record to Postgres.
3. The top 5 ingredients (by importance order) are doubled in the embedding text to bias retrieval toward primary ingredients.
4. `EmbeddingService` calls Titan to generate a 1024-dim vector; the embedding is written back via a native `UPDATE` query (bypassing Hibernate's lack of `vector` type support).
5. The `userRecipes` Spring Cache entry is evicted.

**Recipe parsing / draft** (`POST /api/recipes/draft`):
- Raw text is sent to Claude Haiku, which returns structured JSON with name, description, and ranked ingredients (most essential first).

**Ingredient search** (`POST /api/suggest`):
- The available ingredients string is expanded and embedded via Titan.
- `RecipeRepository.findSimilarRecipes` does a cosine similarity search (`<=>` operator) with a distance threshold of `0.70`.
- If the user has no saved recipes (new user cold-start), `SuggestController.suggestBorrowedRecipes` fetches community recipes from other users at a tighter threshold of `0.60`.
- `NewRecipeSuggestionService` calls Claude Haiku with matched recipes as context to generate a new tailored suggestion.

### Key Design Decisions

- **pgvector embedding column**: The `embedding` column is not managed by Hibernate. It is added via raw SQL after table creation. `RecipeRepository.updateEmbedding` uses a native query with `CAST(:embedding AS vector)`.
- **Ingredient ordering matters**: Ingredients in `Recipe.ingredients` must be ordered from most to least essential — this ordering is used during embedding to weight primary proteins/starches higher by repeating the top-5 in the embedding text.
- **Spring Cache**: `userRecipes` cache is keyed by `userId`. It is evicted on `createRecipe` and `deleteRecipe`. The cache manager is exposed via Actuator at `/actuator/caches`.
- **`userId` propagation**: Controllers extract `userId` from `request.getAttribute("userId")` (set by `JwtFilter`), not from the request body. Never trust user-supplied userId in request bodies.

### Config Profiles

- `application.properties` — shared defaults, active profile defaults to `local`
- `application-local.properties` — local DB (`localhost:5432/recipeapp`) and Cognito config
- `application-prod.properties` — production DB URL, CORS origin, JWT issuer URI (set at Render deploy time via env vars)

### Static Frontend Pages

| Page | File | Purpose |
|------|------|---------|
| Home / Landing | `index.html` | Marketing / login redirect |
| Login | `login.html` | Cognito sign-in/sign-up |
| Recipe Book | `journal.html` | View, add, delete personal recipes |
| Meal Finder | `find.html` | Ingredient search + AI suggestion |

`auth.js` uses AWS Amplify to fetch the current Cognito session and attach the `Authorization: Bearer <token>` header to every API call. A 401 from any API call redirects to `/login`.

## Eval Scripts

`eval/` contains Python scripts for evaluating the RAG pipeline quality:
- `seed_recipes.py` — seeds the DB with test recipes
- `fridge_to_fork_eval.py` — hit-rate and LLM-as-a-judge evaluation
- `cache_evaluation.py` — benchmarks Spring Cache effectiveness

```bash
cd eval
pip install -r requirements.txt
python seed_recipes.py
python fridge_to_fork_eval.py
```
