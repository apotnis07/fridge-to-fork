# Remi - Your Personal AI Chef

A full-stack RAG application that leverages a microservices architecture to record user-created recipes and returns the best matched recipes based on the available ingredients in the users' pantry. It also generates AI suggested recipes that can be made from the available ingredients. Built with Spring Boot, AWS Bedrock, AWS Cognito, Postgres, HTML, CSS and JavaScript. Deployed on Render.

## Architecture

### AWS Cognito, Spring Security - Authentication 

```mermaid
sequenceDiagram
    autonumber
    participant Browser
    participant AuthJS as auth.js
    participant Cognito as AWS Cognito
    participant Spring as Spring Boot
    participant JwtFilter as JwtFilter.java
    participant JWKS as Cognito JWKS URL

    Note over Browser,JWKS: First visit — unauthenticated

    Browser->>AuthJS: Page load on protected route
    AuthJS->>Cognito: fetchAuthSession()
    Cognito-->>AuthJS: No session found
    AuthJS-->>Browser: Redirect to /login

    Note over Browser,JWKS: User signs in

    Browser->>Cognito: Sign in with username and password
    activate Cognito
    Cognito->>Cognito: Validate credentials
    Cognito-->>Browser: accessToken + idToken + refreshToken
    deactivate Cognito

    Note over Browser,JWKS: User navigates to protected page

    Browser->>AuthJS: Page load on protected route
    AuthJS->>Cognito: fetchAuthSession()
    Cognito-->>AuthJS: Valid session — returns accessToken
    AuthJS->>Spring: GET /api/resource with Authorization Bearer token

    activate Spring
    Spring->>JwtFilter: Request intercepted
    activate JwtFilter
    JwtFilter->>JWKS: Fetch RSA public keys
    JWKS-->>JwtFilter: Public key set
    JwtFilter->>JwtFilter: Validate signature, expiry, issuer, audience

    alt Token is valid
        JwtFilter->>Spring: Set SecurityContext as authenticated
        deactivate JwtFilter
        Spring->>Spring: WebSecurityConfig permits request
        Spring-->>Browser: 200 OK — protected resource
        deactivate Spring
    else Token is invalid or expired
        JwtFilter-->>Browser: 401 Unauthorized
        Note over AuthJS,Browser: auth.js catches 401 and redirects to /login
    end

    Note over Browser,JWKS: Background token refresh

    AuthJS->>Cognito: Refresh using refreshToken
    Cognito-->>AuthJS: New accessToken issued
```


### RAG application flow

```mermaid
sequenceDiagram
    autonumber
    participant Browser
    participant AuthJS as auth.js
    participant Spring as Spring Boot
    participant DB as PostgreSQL + pgvector
    participant Bedrock as AWS Bedrock
    participant Titan as Titan Embeddings
    participant Claude as Claude (LLM)

    Note over Browser,Claude: Landing and authentication

    Browser->>AuthJS: Land on home page
    AuthJS->>AuthJS: fetchAuthSession()

    alt Not authenticated
        AuthJS-->>Browser: Redirect to /login
        Browser->>AuthJS: Sign in via Cognito
        AuthJS-->>Browser: accessToken issued
    end

    Browser-->>Browser: Redirect to /recipe-book

    Note over Browser,Claude: Recipe book — load existing recipes

    Browser->>Spring: GET /api/recipes (Bearer token)
    Spring->>DB: SELECT recipes for user
    DB-->>Spring: Recipe rows
    Spring-->>Browser: 200 — recipe list

    Note over Browser,Claude: User describes a recipe and clicks record

    Browser->>Spring: POST /api/recipes/draft — recipe description
    activate Spring
    Spring->>Bedrock: Invoke Claude with description
    activate Bedrock
    Bedrock->>Claude: Parse ingredients and recipe details
    Claude-->>Bedrock: Structured recipe response
    deactivate Bedrock
    Spring-->>Browser: 200 — parsed recipe preview
    deactivate Spring

    Note over Browser,Claude: User reviews and confirms the parsed recipe

    Browser->>Spring: POST /api/recipes — save confirmed recipe
    activate Spring
    Spring->>Bedrock: Invoke Titan Embeddings with recipe text
    activate Bedrock
    Bedrock->>Titan: Generate vector embedding
    Titan-->>Bedrock: Embedding vector
    deactivate Bedrock
    Spring->>DB: INSERT recipe row with embedding column
    DB-->>Spring: Saved
    deactivate Spring
    Spring-->>Browser: 201 — recipe created

    Note over Browser,Claude: Meal finder — load and search

    Browser->>Spring: GET /api/recipes (Bearer token)
    Spring->>DB: SELECT latest recipes for user
    DB-->>Spring: Recipe rows
    Spring-->>Browser: 200 — recipes displayed

    Browser->>Spring: POST /api/suggest — available ingredients
    activate Spring
    Spring->>Bedrock: Invoke Titan Embeddings with ingredients
    activate Bedrock
    Bedrock->>Titan: Generate embedding from ingredients
    Titan-->>Bedrock: Ingredient embedding vector
    deactivate Bedrock
    Spring->>DB: Cosine similarity search using pgvector
    DB-->>Spring: Top matching recipes

    Spring->>Bedrock: Invoke Claude with ingredients
    activate Bedrock
    Bedrock->>Claude: Generate new recipe suggestion
    Claude-->>Bedrock: Recipe suggestion
    deactivate Bedrock
    deactivate Spring

    Spring-->>Browser: 200 — matched recipes + new suggestion
```

### Collapsed diagram

```mermaid
sequenceDiagram
    autonumber
    participant Browser
    participant AuthJS as auth.js
    participant Cognito as AWS Cognito
    participant Spring as Spring Boot
    participant DB as PostgreSQL + pgvector
    participant Bedrock as AWS Bedrock

    Note over Browser,Bedrock: Landing and authentication

    Browser->>AuthJS: Land on home page
    AuthJS->>Cognito: fetchAuthSession()

    alt Not authenticated
        Cognito-->>AuthJS: No session found
        AuthJS-->>Browser: Redirect to /login
        Browser->>Cognito: Sign in with credentials
        Cognito-->>Browser: accessToken issued
    end

    Browser-->>Browser: Redirect to /recipe-book

    Note over Browser,Bedrock: Recipe book

    Browser->>Spring: GET /api/recipes (Bearer token)
    Spring->>DB: SELECT recipes for user
    DB-->>Spring: Recipe rows
    Spring-->>Browser: 200 — recipe list

    Browser->>Spring: POST /api/recipes/draft — recipe description
    Spring->>Bedrock: Claude parses ingredients from description
    Bedrock-->>Spring: Structured recipe preview
    Spring-->>Browser: 200 — parsed recipe preview

    Browser->>Spring: POST /api/recipes — confirm and save
    Spring->>Bedrock: Titan generates embedding from recipe text
    Bedrock-->>Spring: Embedding vector
    Spring->>DB: INSERT recipe row with embedding column
    Spring-->>Browser: 201 — recipe created

    Note over Browser,Bedrock: Meal finder

    Browser->>Spring: GET /api/recipes (Bearer token)
    Spring->>DB: SELECT latest recipes for user
    DB-->>Spring: Recipe rows
    Spring-->>Browser: 200 — recipes displayed

    Browser->>Spring: POST /api/suggest — available ingredients
    Spring->>Bedrock: Titan generates embedding from ingredients
    Bedrock-->>Spring: Ingredient embedding vector
    Spring->>DB: Cosine similarity search using pgvector
    DB-->>Spring: Top matching recipes
    Spring->>Bedrock: Claude suggests new recipe from ingredients
    Bedrock-->>Spring: New recipe suggestion
    Spring-->>Browser: 200 — matched recipes + new suggestion
```
