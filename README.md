# Remi - Your Personal AI Chef

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring%20Security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-black?style=for-the-badge&logo=JSON%20web%20tokens)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![AWS](https://img.shields.io/badge/AWS-%23FF9900.svg?style=for-the-badge&logo=amazon-aws&logoColor=white)
![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-231F20?style=for-the-badge&logo=apachekafka&logoColor=white)

A full-stack RAG application that leverages a microservices architecture to record user-created recipes and returns the best matched recipes based on the available ingredients in the users' pantry. It also generates AI suggested recipes that can be made from the available ingredients. Recipe and search activity is streamed as domain events to Apache Kafka. Built with Spring Boot, AWS Bedrock, AWS Cognito, Postgres, Apache Kafka, HTML, CSS and JavaScript. Deployed on Render.

## Video Demos

### Home & Login Page

<div align="left">
  <img src="src/main/resources/static/assets/demo/Home.gif" width="95%" alt="Home & Login">
</div>

### Save Recipe

<div align="left">
  <img src="src/main/resources/static/assets/demo/SaveRecipe.gif" width="95%" alt="Save Recipe">
</div>

### Delete Recipe

<div align="left">
  <img src="src/main/resources/static/assets/demo/DeleteRecipe.gif" width="95%" alt="Delete Recipe">
</div>

### Search Recipe, Remi Suggests - AI-generated recipe, Community-sourced recipe

<div align="left">
  <img src="src/main/resources/static/assets/demo/SearchRecipe.gif" width="95%" alt="Search Recipe">
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

    subgraph Kafka["Apache Kafka (KRaft)"]
        RecipeTopic["remi.recipe-events"]
        SearchTopic["remi.search-events"]
    end

    Browser -- "sign in / sign up" --> Cognito
    Cognito -- "accessToken (JWT)" --> AuthJS
    AuthJS -- "Bearer token on all requests" --> Security

    Security --> RecipeAPI
    Security --> DraftAPI
    Security --> SuggestAPI

    DraftAPI -- "parse ingredients" --> Claude
    SuggestAPI -- "suggest new recipe with matches as context" --> Claude

    RecipeAPI -- "generate embedding on save" --> Titan
    SuggestAPI -- "embed available ingredients" --> Titan

    RecipeAPI -- "read / write recipes" --> PG
    SuggestAPI -- "cosine similarity search" --> PGV
    PG --- PGV

    RecipeAPI -. "RecipeSaved / RecipeDeleted (after DB commit)" .-> RecipeTopic
    SuggestAPI -. "SearchPerformed" .-> SearchTopic
```

Remi is a full-stack application built across three layers. The frontend is served by Spring Boot with JavaScript handling client-side authentication state via AWS Amplify. The backend is a REST API built with Spring Boot, responsible for all business logic, external service orchestration, and data persistence. All data is stored in a PostgreSQL database extended with pgvector, which enables the application to store and query vector embeddings natively alongside relational data.
Authentication is handled by AWS Cognito, which manages user identity, credential validation, and token lifecycle. AI capabilities are powered by AWS Bedrock, which provides access to two models: Claude for natural language understanding and recipe generation, and Amazon Titan for converting text into vector embeddings. 
Recipe saves, deletes and searches are also published as domain events to Apache Kafka. Publishing is best-effort and never blocks or fails a user's request (see [Event Streaming](#event-streaming-apache-kafka)).

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
## Evaluation

|Metric| Score|
|------|-------|
|Hit Rate @1| 80.0%|
|Hit Rate @3| 93.3%|
|Retrieval Relevance: | 4.6/5|
|Faithfulness:| 4.69/5|
|Practicality:| 4.11/5|
|Overall Quality:| 4.41/5|

To ensure the reliability of the RAG pipeline, a custom LLM-as-a-Judge evaluation was implemented. The system was benchmarked against synthetic test cases of varying difficulty (Easy, Medium, Hard) to quantify retrieval accuracy and generation faithfulness.

* Hit Rate @ 1: 80.0% — The most relevant recipe is correctly retrieved as the top result in 4 out of 5 cases.

* Hit Rate @ 3: 93.3% — The system successfully identifies the target recipe within the top 3 results for nearly all queries.

* Average Faithfulness: 4.69/5 — The AI-generated suggestions strictly adhere to the user's available ingredients.

* Retrieval Relevance: 4.6/5 — Semantic search effectively bridges the gap between natural language input and stored recipes.
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
* **Taste-Informed Logic:** When suggesting a new meal, the LLM is fed the matched recipes for the available ingredients from your existing recipe history as "context." This ensures suggestions align with your personal cooking style and flavor preferences.
* **Cold-Start Solution (Community Recipes)**: To ensure value for new users, the engine intelligently "borrows" highly-rated community recipes when a user's personal "Recipe Book" is still growing.
---

## Event Streaming (Apache Kafka)

Every recipe save, recipe delete and ingredient search is published as an immutable domain event to Kafka, creating a per-user activity stream for analytics, without coupling that work to the request path.

| Event | Topic | Key | Published when |
|---|---|---|---|
| `RecipeSaved` | `remi.recipe-events` | `userId` | After the save transaction commits |
| `RecipeDeleted` | `remi.recipe-events` | `userId` | After the delete commits, only if a row was actually deleted |
| `SearchPerformed` | `remi.search-events` | `userId` | Right after retrieval, before the Claude suggestion |

Example payload:

```json
{"eventType":"RecipeSaved","schemaVersion":1,"userId":"customer-1","recipeId":"88c11bc9-1905-406b-a3c8-1e6f33ba3658","ingredients":["potatoes","milk","butter","garlic"],"timestamp":"2026-11-05T06:11:12.689155Z"}
```

**Design decisions**
* **Keyed by `userId`:** all of a user's events land in one partition, so their order is preserved (a save always precedes its delete) and per-user aggregations need no repartitioning. `userId` is also kept in the value, so the key can change later without losing it.
* **Publish after commit:** recipe events go through a Spring `@TransactionalEventListener(phase = AFTER_COMMIT)`, so Kafka never announces a change that was rolled back (the dual-write problem). Searches don't write to the DB, so they publish directly.
* **Durable, idempotent producer:** `acks=all` with `enable.idempotence=true`, so producer retries don't create duplicates. End to end the pipeline is at-least-once, so consumers should tolerate duplicates.
* **Never blocks or fails the user:** `max.block.ms=500` caps how long a request can wait on Kafka, and `EventPublisher.publish()` catches and logs both synchronous and asynchronous send failures instead of throwing. If the broker is down, requests respond normally and events are logged and dropped. The outbox pattern is the upgrade path if guaranteed delivery is ever needed.
* **Feature flag:** `remi.events.enabled` (default `false`) turns publishing on or off, and topic auto-creation follows the same flag, so an environment without a broker never tries to connect.
* **Schema stability:** events are Java records with `eventType` and `schemaVersion` fields and ISO-8601 timestamps. Spring's Java type headers are turned off, so renaming a class doesn't break consumers.
* **Single publishing boundary:** `EventPublisher` owns topic routing, keys, the flag and error handling, so `RecipeService` and `SuggestController` don't depend on Kafka.

Covered by `EventSerializationTest` and `EventPublisherTest` (including the sync `thenThrow` and async `failedFuture` failure paths). The reasoning behind each choice is recorded in [decision_log.md](decision_log.md).

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Tailwind CSS, HTML, JavaScript |
| API Gateway | Spring Boot 3.3 |
| AI Service | Claude Haiku 4.5, Titan Embeddings v2 |
| Event Streaming | Apache Kafka 4.4 (KRaft), Spring Kafka |
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
├── decision_log.md                 # Kafka design decisions and trade-offs
├── docker-compose.yml              # Postgres + pgvector, Kafka broker
├── .env
├── mvnw
├── mvnw.cmd
├── pom.xml
└── src
    ├── main
    │   ├── java
    │   │   └── com
    │   │       └── example
    │   │           └── fridge_to_fork
    │   │               ├── FridgeToForkApplication.java
    │   │               ├── config
    │   │               │   ├── BedrockConfig.java
    │   │               │   ├── JwtFilter.java
    │   │               │   ├── KafkaTopicConfig.java
    │   │               │   └── WebSecurityConfig.java
    │   │               ├── controller
    │   │               │   ├── RecipeController.java
    │   │               │   └── SuggestController.java
    │   │               ├── event
    │   │               │   ├── RemiEvent.java
    │   │               │   ├── RecipeSaved.java
    │   │               │   ├── RecipeDeleted.java
    │   │               │   └── SearchPerformed.java
    │   │               ├── messaging
    │   │               │   ├── EventPublisher.java
    │   │               │   └── RemiEventRelay.java
    │   │               ├── model
    │   │               │   ├── Ingredient.java
    │   │               │   ├── Recipe.java
    │   │               │   ├── SuggestionRequest.java
    │   │               │   └── SuggestionResult.java
    │   │               ├── repository
    │   │               │   └── RecipeRepository.java
    │   │               ├── service
    │   │               │   ├── EmbeddingService.java
    │   │               │   ├── NewRecipeSuggestionService.java
    │   │               │   ├── RecipeParsingService.java
    │   │               │   └── RecipeService.java
    │   │               └── util
    │   │                   └── IngredientConverter.java
    │   └── resources
    │       ├── META-INF
    │       │   └── additional-spring-configuration-metadata.json
    │       ├── application-local.properties
    │       ├── application-prod.properties
    │       ├── application.properties
    │       └── static
    │           ├── assets
    │           ├── auth.js
    │           ├── find.html
    │           ├── index.html
    │           ├── journal.html
    │           └── login.html
    └── test
        └── java/com/example/fridge_to_fork
            ├── event
            │   └── EventSerializationTest.java
            └── messaging
                └── EventPublisherTest.java

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
# Starts Postgres (pgvector) and the Kafka broker defined in docker-compose.yml
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

### Kafka events (optional)

Event publishing is off by default. To turn it on locally, add this to `application-local.properties`:

```bash
remi.events.enabled=true
```

On startup the app creates the `remi.recipe-events` and `remi.search-events` topics (3 partitions each) on the broker at `localhost:9092`. To watch events as you save, delete and search recipes:

```bash
docker exec -it recipe-broker /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic remi.recipe-events --from-beginning --property print.key=true
```

Swap in `remi.search-events` to see searches. If the broker is stopped, the app keeps working and logs the failed sends.

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
remi.events.enabled=false
```
7. Commit and push.

> **Kafka in production:** the Render free tier has no Kafka broker, so `application-prod.properties` sets `remi.events.enabled=false`. The app neither connects to a broker nor creates topics. To enable events in production, point `spring.kafka.bootstrap-servers` at a managed Kafka cluster and set the flag to `true`.
