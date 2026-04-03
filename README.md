# Remi - Your Personal AI Chef

A full-stack RAG application that leverages a microservices architecture to record user-created recipes and returns the best matched recipes based on the available ingredients in the users' pantry. It also generates AI suggested recipes that can be made from the available ingredients. Built with Spring Boot, AWS Bedrock, AWS Cognito, Postgres, HTML, CSS and JavaScript. Deployed on Render.

## Architecture

```mermaid
flowchart TB
    subgraph Client["Client"]
        Browser["Browser"]
        AuthJS["auth.js (Amplify)"]
    end

    subgraph AWS["AWS Cognito"]
        Cognito["AWS Cognito\nUser Pool"]
    end

    subgraph Bedrock["AWS Bedrock"]
        Claude["Claude\nLLM"]
        Titan["Titan\nEmbeddings"]
    end

    subgraph Backend["Spring Boot Backend"]
        Security["Spring Security + JWT Filter"]
        subgraph Endpoints["API Endpoints"]
            RecipeAPI["/api/recipes"]
            SuggestAPI["/api/suggest"]
            DraftAPI["/api/recipes/draft"]
        end
        RecipeAPI ~~~ DraftAPI ~~~ SuggestAPI
    end

    subgraph Database["Database"]
        PG["PostgreSQL"]
        PGV["pgvector\nextension"]
    end

    Browser -- "sign in / sign up" --> Cognito
    Cognito -- "accessToken (JWT)" --> AuthJS
    AuthJS -- "Bearer token on all requests" --> Security

    Security --> RecipeAPI
    Security --> DraftAPI
    Security --> SuggestAPI

    DraftAPI -- "parse ingredients" --> Claude
    SuggestAPI -- "suggest new recipe" --> Claude

    RecipeAPI -- "generate embedding on save" --> Titan
    SuggestAPI -- "embed available ingredients" --> Titan

    RecipeAPI -- "read / write recipes" --> PG
    SuggestAPI -- "cosine similarity search" --> PGV
    PG --- PGV
```

### AWS Cognito, Spring Security - Authentication 
<details>
<summary>See Authentication Request Flow</summary>
    
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

</details>
    



### RAG application flow

<details>
<summary>See RAG Application Flow</summary>

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
</details>

