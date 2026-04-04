# Remi - Your Personal AI Chef

A full-stack RAG application that leverages a microservices architecture to record user-created recipes and returns the best matched recipes based on the available ingredients in the users' pantry. It also generates AI suggested recipes that can be made from the available ingredients. Built with Spring Boot, AWS Bedrock, AWS Cognito, Postgres, HTML, CSS and JavaScript. Deployed on Render.

## Website

Live Demo: [fridge-to-fork-eyju.onrender.com](https://fridge-to-fork-eyju.onrender.com/)

(Note: As this is hosted on a free tier, please allow ~60 seconds for the server to spin up on your first visit.)

## Video Demos

### Home & Login Page

![Home & Login](src/main/resources/static/assets/demo/home_login.gif)

### Save Recipe

![Save Recipe](src/main/resources/static/assets/demo/SaveRecipe.gif)

### Delete Recipe

![Delete Recipe](src/main/resources/static/assets/demo/DeleteRecipe.gif)

### Search Recipe, Remi Suggests - AI-generated recipe, Community-sourced recipe

<div align="left">
  <img src="src/main/resources/static/assets/demo/SearchRecipe.gif" width="100%" alt="Search Recipe">
</div>

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

Remi is a full-stack application built across three layers. The frontend is served by Spring Boot with JavaScript handling client-side authentication state via AWS Amplify. The backend is a REST API built with Spring Boot, responsible for all business logic, external service orchestration, and data persistence. All data is stored in a PostgreSQL database extended with pgvector, which enables the application to store and query vector embeddings natively alongside relational data.
Authentication is handled by AWS Cognito, which manages user identity, credential validation, and token lifecycle. AI capabilities are powered by AWS Bedrock, which provides access to two models: Claude for natural language understanding and recipe generation, and Amazon Titan for converting text into vector embeddings. 

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

Remi uses a JWT-based authentication supported by AWS Cognito. Amplify Auth provides methods to handle functions like sign-in, sign-out, and managing the tokens. Spring Security restricts the endpoints that can be accessed by the user depending on their session status. In every request, a Bearer token is passed which is verified by a JWT Filter by using the JWKS URL that has the Cognito public key. 

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


SpringBoot serves the frontend static links and handles the API calls made to fetch, save, and suggest recipes. It also manages the API calls that trigger LLM calls to AWS Bedrock, which in turn makes calls to two separate models, the Claude Haiku 4.5 and Titan Embeddings v2. 

The application takes user input in the form of natural language and parses it to identify the ingredients in the recipe and records the quantities when available. These ingredients are ranked based on their importance to the dish, and once the user confirms the parsing, they are converted into vector embeddings with additional importance given to the primary ingredients to accommodate the cases when the user only searches for the primary ingredient of the dish. These recipes are saved in Postgres and utilizes pgvector for handling calculating cosine similarity when searching for the most similar recipes based on the ingredients user has available.

When a user searches for recipes, an LLM call is also made to generate a new recipe based on the matches from the user's own recipes (puts the user's taste in context) and the available ingredients. 
</details>

---

Since you are building this for your portfolio and Medium, the text should highlight the **technical complexity** while keeping the **user benefit** clear. 

Here is the refined text for those three key features, formatted for a professional README:

---

## Key Features

### 1. Intelligent Recipe Parsing (Claude 4.5 Haiku)
Remi transforms unstructured natural language into structured data. Users can paste a messy list of ingredients or a brief description of a dish, and the application utilizes **Claude 4.5 Haiku** to:
* **Extract & Normalize:** Identify ingredients and quantities from free-text.
* **Weighted Importance:** Automatically rank ingredients by their role in the dish (e.g., "Chicken" is prioritized over "Salt").
* **Interactive Preview:** Provides a structured "Draft" state, allowing users to verify and edit parsed data before it hits the database.



### 2. Semantic Ingredient Search (Vector Embeddings)
* **Titan Embeddings:** Converts recipe ingredients into 1024-dimensional vectors.
* **pgvector & Cosine Similarity:** Executes high-performance vector similarity searches within PostgreSQL to find recipes that best match the available ingredients in your pantry.
* **Primary Ingredient Bias:** The embedding logic is tuned to give higher mathematical weight to core proteins and vegetables, ensuring matches are culinarily relevant, not just statistically similar.



### 3. Context-Aware AI Suggestions (RAG)
Remi goes beyond just searching your existing "Recipe Book." It uses **Retrieval-Augmented Generation (RAG)** to act as a creative chef.
* **Taste-Informed Logic:** When suggesting a new meal, the LLM is fed the matches for the available ingredients from your existing recipe history as "context." This ensures suggestions align with your personal cooking style and flavor preferences.
* **Community Recipes:** If there are a low number of available recipes, in the user's recipe book it borrows recipes from the community.
---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Google Stitch, Tailwind CSS, HTML, JavaScript |
| API Gateway | Spring Boot 4.0 |
| AI Service | Claude Haiku 4.5, Titan Embeddings v2 |
| Containerization | Docker, Docker Compose |
| Security | AWS Cognito, JWT, Spring Security, Amplify Auth |
| Cloud | Render |
| Build | Maven |

---

## Project Structure

```
.
├── Dockerfile
├── HELP.md
├── README.md
├── docker-compose.yml
├── .env
├── mvnw
├── mvnw.cmd
├── pom.xml
└── src
    └── main
        ├── java
        │   └── com
        │       └── example
        │           └── fridge_to_fork
        │               ├── FridgeToForkApplication.java
        │               ├── config
        │               │   ├── BedrockConfig.java
        │               │   ├── JwtFilter.java
        │               │   └── WebSecurityConfig.java
        │               ├── controller
        │               │   ├── RecipeController.java
        │               │   └── SuggestController.java
        │               ├── model
        │               │   ├── Ingredient.java
        │               │   ├── Recipe.java
        │               │   ├── SuggestionRequest.java
        │               │   └── SuggestionResult.java
        │               ├── repository
        │               │   └── RecipeRepository.java
        │               ├── service
        │               │   ├── EmbeddingService.java
        │               │   ├── NewRecipeSuggestionService.java
        │               │   └── RecipeParsingService.java
        │               └── util
        │                   └── IngredientConverter.java
        └── resources
            ├── META-INF
            │   └── additional-spring-configuration-metadata.json
            ├── application-local.properties
            ├── application-prod.properties
            ├── application.properties
            └── static
                ├── assets
                ├── auth.js
                ├── find.html
                ├── index.html
                ├── journal.html
                └── login.html

```

---

## Setup

### Prerequisites

- Java 21+
- Docker Desktop
- AWS CLI (for deployment only)
- Beekeeper Studio or similar (optional)

## Local Implementation

Save local DB credentials in a .env file placed at the root of the project.
```bash
DB_PASSWORD=your_password_here
DB_USERNAME=your_username_here
```

The attributes defined in the Recipe.java class are used by Hibernate to create a table in the DB if one is not already present. However when developing the project, there were some challenges with creating an embeddings column directly through Hibernate because Hibernate does not know how to map a vector(1024) PostgreSQL type to a java type out of the box. To work around this, the table is created through the attributes defined in Recipe.java, and an ALTER TABLE command is then run later to add the embedding column and bypass Hibernate altogether. 

The instructions to do this are as follows:

1. Ensure Docker Desktop is running and AWS profile is logged in.

To set up use
```bash
aws configure
```

To verify use
```bash
aws configure list
```

IMPORTANT: The AWS profile should be logged in the terminal where the following Docker and Maven commands will be executed. Otherwise, AWS Bedrock won't have user credentials.

```bash
# The docker compose up command starts the frontend and backend services as per the configurations in docker-compose.yml file.
docker-compose up -d
./mvnw spring-boot:run
```

2. If Beekeeper Studio (or similar) is available,

```bash
CREATE EXTENSION IF NOT EXISTS vector;
ALTER TABLE recipes ADD COLUMN IF NOT EXISTS embedding vector(1024);
```

2. If it is not available,

```bash
# Open a new terminal
docker exec -it recipe-db psql -U postgres -d recipeapp
CREATE EXTENSION IF NOT EXISTS vector;
ALTER TABLE recipes ADD COLUMN IF NOT EXISTS embedding vector(1024);
\q
```

Now your Postgres DB is ready.

---

You can now access the app at http://localhost:8080

## Deployment

For the deployment of this application, a few options were explored and here are the pros and cons for each approach

|Setup| Frontend | Backend | Database | Pros | Cons |
|---|----------|---------|----------|------|------|
|1| AWS Amplify | AWS Elastic Beanstalk (EB) | RDS | The best possible architecture, where Amplify serves the static frontend and EB creates the server, with RDS storing the recipes. All services are based in the AWS ecosystem | Elastic Beanstalk creates the server and while doing so it creates an http endpoint, however Amplify requires the backend service to have a https endpoint. This can be worked around by using Route 53 or buying a domain that would come with a certificate. |
|2| AWS Elastic Beanstalk (EB) | AWS Elastic Beanstalk (EB) | RDS | With frontend and backend hosted in EB we can work around Amplify requiring an https endpoint. EB stays live and does not suffer cold starts of servers | This time, it is Cognito that requires an https endpoint if the redirect or sign-out link is anything other than localhost. The solution is buying a domain or using Route 53. |
|3| Render | Render | RDS | Deploying frontend and backend to Render. Render offers a free tier that keeps the website live. RDS offers permanent data storage | RDS still charges to store data on AWS. |
|4| Render | Render | Render(Postgres) | Deploying to Render and using the Postgres DB offered by Render keeps the whole application in a single ecosystem and is truly free. | The caveats here are that in the Render free tier the servers spin down after 15 mins of inactivity and take upto a minute to start when a new request is received outside of this window. Also, the data in the DB for the free tier is persisted only for 1 month. |

---

### Setup 4 - Render + Render Postgres (Free tier)

#### Render PostgreSQL
1. Render dashboard → New → PostgreSQL
2. Name - fridge-to-fork-db, Region - Ohio (US East), PostgreSQL version - 16, Instance type - Free
3. Create DB
4. Once created, get the Internal DB URL, example - postgresql://fridge_to_fork_db_user:password@dpg-xxxxx-a/fridge_to_fork_db

#### Render 
1. Render -> New -> Web Service
2. Connect GitHub, add repo and branch.
3. Configure the service
   
|Field|Value|
|--|----|
|Name|fridge-to-fork|
|Language|Docker|
|Region|US East (Ohio)|
|Branch|`your_branch`|
|Instance type| Free|

4. Add environment variables - generate aws access key and secret access key from aws

|Key|Value|
|--|----|
|AWS_ACCESS_KEY_ID|`your_key_id`|
|AWS_REGION|`your_region`
|AWS_SECRET_ACCESS_KEY|`your_secret_access_key`|
|SPRING_PROFILES_ACTIVE|prod|
|SPRING_DATASOURCE_URL|`jdbc:postgresql://dpg-xxxxx-a/fridge_to_fork_db`|
|SPRING_DATASOURCE_USERNAME|`from Render DB details`|
|SPRING_DATASOURCE_PASSWORD|`from Render DB details`|

5. Create web service.
6. After deployment, in application-prod.properties
```bash
spring.security.oauth2.resourceserver.jwt.issuer-uri=your_url
app.cors.allowed-origins=your_render_url
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
server.port=8080
```
7. Commit and push.
