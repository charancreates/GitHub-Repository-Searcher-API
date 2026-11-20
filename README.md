# GitHub Repository Searcher API

A **Spring Boot** RESTful backend that searches GitHub repositories via the GitHub REST API, persists them in **PostgreSQL** with automatic upsert (zero duplicates), and exposes dynamic filtering endpoints documented with **Swagger/OpenAPI**.

> **In simple terms:** Ask this API for any GitHub topic (e.g., _"Spring Boot in Java"_), and it fetches the top matching repositories from GitHub, saves/updates them in PostgreSQL, and lets you query and filter your saved collection anytime.

---

## Tech Stack

| Category                        | Technology                                                                   |
| :------------------------------ | :--------------------------------------------------------------------------- |
| **Language & Framework**        | Java 17, Spring Boot 3.3.5                                                   |
| **HTTP Client**                 | Spring `RestClient` (configured with GitHub headers & optional Bearer auth)  |
| **Database & ORM**              | PostgreSQL (Neon), Spring Data JPA, Hibernate                                |
| **Validation & Error Handling** | Jakarta Bean Validation (`@Valid`), `@RestControllerAdvice`                  |
| **API Documentation**           | Springdoc OpenAPI 3 (Swagger UI)                                             |
| **Testing**                     | JUnit 5, Mockito, Spring `MockMvc` (`@WebMvcTest`)                           |

---

## System Architecture

The application follows a strict **4-layer architecture** to keep HTTP contracts, business logic, and database operations cleanly decoupled:

```text
                       Client (Swagger UI / Postman / cURL)
                                        │
                                        ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  1. Controller Layer (GitHubController.java)                                 │
│     • Exposes POST /api/github/search & GET /api/github/repositories         │
│     • Enforces input validation (@Valid, @NotBlank, @Pattern)                │
└───────────────────────────────────────┬──────────────────────────────────────┘
                                        │
                                        ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  2. Service Layer (GitHubService.java)                                       │
│     • Builds GitHub query strings & resolves Sort parameters                 │
│     • Calls GitHub REST API via RestClientConfig (1 call = up to 30 repos)   │
│     • Maps GitHub JSON <-> RepositoryEntity <-> RepositoryDto                │
└───────────────┬───────────────────────────────────────────────┬──────────────┘
                │                                               │
                ▼                                               ▼
┌───────────────────────────────┐             ┌────────────────────────────────┐
│  External GitHub REST API     │             │  3. Repository Layer           │
│  /search/repositories         │             │  (GitHubRepositoryRepo.java)   │
│  • Handles 403/429 rate limit │             │  • JPA saveAll() upsert        │
│  • Ignores unknown JSON keys  │             │  • Native PostgreSQL filtering │
└───────────────────────────────┘             └─────────────────┬──────────────┘
                                                                │
                                                                ▼
                                              ┌────────────────────────────────┐
                                              │  4. Database Layer             │
                                              │  PostgreSQL (Neon)             │
                                              │  Table: github_repositories    │
                                              └────────────────────────────────┘

  [Cross-Cutting] GlobalExceptionHandler (@RestControllerAdvice)
  Catches Validation (400), Rate Limit (429), Upstream API (502), & Generic (500) errors.
```

---

## End-to-End Data Flows

### Flow 1: Search & Upsert (`POST /api/github/search`)

1. **Request Validation:** `GitHubController` deserializes the incoming JSON into `SearchRequestDto` and runs Jakarta Validation (`@NotBlank` on `query`, `@Pattern` restricting `sort` to `stars`, `forks`, or `updated`). Invalid payloads immediately return `400 Bad Request`.
2. **GitHub API Call:** `GitHubService` combines `query` and optional `language` into GitHub's search syntax (e.g., `spring boot language:Java`) and issues a single `GET` request via `RestClient` to `https://api.github.com/search/repositories`.
   - _Note:_ GitHub returns up to **30 repositories in a single API call** by default, consuming only 1 request against your rate limit.
3. **Selective Deserialization:** Jackson maps the response into `GitHubApiResponse` using `@JsonIgnoreProperties(ignoreUnknown = true)` and `@JsonProperty` annotations to extract only the 8 required fields (`id`, `name`, `description`, `owner.login`, `language`, `stargazers_count`, `forks_count`, `updated_at`).
4. **Transactional Upsert:** Inside a `@Transactional` boundary, entities are persisted with `repositoryRepo.saveAll(entities)`.
   - **How Upsert Works:** `RepositoryEntity` uses GitHub's unique repository `id` directly as the JPA `@Id` primary key (no auto-increment). Hibernate checks if the `id` exists—running an `UPDATE` if it is already stored or an `INSERT` if it is new, guaranteeing **zero duplicates**.
5. **Response Serialization:** Saved entities are mapped to `RepositoryDto`, wrapped in `SearchResponseDto`, and serialized back to JSON (`200 OK`). If GitHub returns 0 items, the API gracefully returns `200 OK` with an empty list `[]`.

### Flow 2: Filtered Retrieval (`GET /api/github/repositories`)

1. **Optional Query Parameters:** Accepts `language` (optional), `minStars` (optional), and `sort` (optional, defaults to `stars`).
2. **Validation & Sorting:** `GitHubService` rejects negative `minStars` (`400 Bad Request`) and maps `sort` (`stars`, `forks`, `updated`) to a descending Spring Data `Sort` object.
3. **Native PostgreSQL Query with Text Casts:** `GitHubRepositoryRepo` executes a native PostgreSQL query using explicit text casts (e.g., `CAST(:language AS TEXT)`).
   - **Why Native SQL + Text Casts?** PostgreSQL enforces strict parameter type inference when a nullable parameter (`NULL`) is passed into `LOWER()`. Explicitly casting the parameter to `TEXT` eliminates type-inference errors while keeping language filtering case-insensitive (`java` matches `Java`).
4. **Response:** Matching rows are mapped to `RepositoryDto` and returned inside `RepositoriesResponseDto`.

---

## API Specifications

### 1. Search & Save GitHub Repositories

- **Endpoint:** `POST /api/github/search`
- **Request Body:**

```json
{
  "query": "spring boot",
  "language": "Java",
  "sort": "stars"
}
```

- **Success Response (`200 OK`):**

```json
{
  "message": "Repositories fetched and saved successfully",
  "repositories": [
    {
      "id": 123456,
      "name": "spring-boot-example",
      "description": "An example repository for Spring Boot",
      "owner": "user123",
      "language": "Java",
      "stars": 450,
      "forks": 120,
      "lastUpdated": "2024-01-01T12:00:00Z"
    }
  ]
}
```

### 2. Retrieve Stored Repositories

- **Endpoint:** `GET /api/github/repositories`
- **Query Parameters:**
  - `language` _(optional)_ — Case-insensitive language filter (e.g., `Java`)
  - `minStars` _(optional)_ — Minimum star count (e.g., `100`)
  - `sort` _(optional)_ — `stars` (default), `forks`, or `updated`
- **Example Request:**

```bash
curl "http://localhost:8080/api/github/repositories?language=Java&minStars=100&sort=stars"
```

- **Success Response (`200 OK`):**

```json
{
  "repositories": [
    {
      "id": 123456,
      "name": "spring-boot-example",
      "description": "An example repository for Spring Boot",
      "owner": "user123",
      "language": "Java",
      "stars": 450,
      "forks": 120,
      "lastUpdated": "2024-01-01T12:00:00Z"
    }
  ]
}
```

---

## Error Handling

All exceptions are intercepted by `GlobalExceptionHandler` (`@RestControllerAdvice`) and returned as structured JSON:

| HTTP Status                 | Trigger Scenario                                                 |
| :-------------------------- | :--------------------------------------------------------------- |
| `400 Bad Request`           | Blank `query`, invalid `sort` value, or negative `minStars`.     |
| `429 Too Many Requests`     | GitHub API rate limit exceeded (`403`/`429` from upstream).      |
| `502 Bad Gateway`           | GitHub API unreachable or returned an unexpected upstream error. |
| `500 Internal Server Error` | Unhandled server exception.                                      |

---

## Project Structure

```text
github-repo-searcher/
├── pom.xml                             # Maven dependencies & build plugins
├── .env.example                        # Safe environment variable template
├── src/
│   ├── main/
│   │   ├── java/com/assignment/githubsearcher/
│   │   │   ├── GithubSearcherApplication.java
│   │   │   ├── config/
│   │   │   │   ├── OpenApiConfig.java          # Swagger UI metadata
│   │   │   │   └── RestClientConfig.java       # GitHub RestClient bean & headers
│   │   │   ├── controller/
│   │   │   │   └── GitHubController.java       # REST endpoints
│   │   │   ├── dto/                            # Request, Response, & GitHub API DTOs
│   │   │   ├── entity/
│   │   │   │   └── RepositoryEntity.java       # JPA entity (github_repositories)
│   │   │   ├── exception/
│   │   │   │   ├── GitHubApiException.java
│   │   │   │   └── GlobalExceptionHandler.java # Centralized error handling
│   │   │   ├── repository/
│   │   │   │   └── GitHubRepositoryRepo.java   # JPA CRUD + Native PostgreSQL query
│   │   │   └── service/
│   │   │       └── GitHubService.java          # Business logic & DTO mapping
│   │   └── resources/
│   │       └── application.properties          # Externalized config + .env import
│   └── test/
│       └── java/com/assignment/githubsearcher/
│           ├── controller/GitHubControllerTest.java  # @WebMvcTest + MockMvc
│           └── service/GitHubServiceTest.java        # JUnit 5 + Mockito
```

---

## Running Locally

### Prerequisites

- **Java 17+**
- **Maven 3.9+**
- A **PostgreSQL** database (local or free cloud instance on [Neon](https://neon.tech))

### 1. Configure Environment Variables

Credentials are never hardcoded. `application.properties` uses `spring.config.import=optional:file:.env[.properties]` to load local secrets automatically.

Copy the template and fill in your credentials:

```bash
cp .env.example .env
```

Example `.env`:

```properties
DB_URL=jdbc:postgresql://<YOUR_NEON_HOST>/<YOUR_DB>?sslmode=require
DB_USERNAME=<YOUR_DB_USERNAME>
DB_PASSWORD=<YOUR_DB_PASSWORD>
GITHUB_TOKEN=           # Optional: GitHub Personal Access Token for higher rate limits
```

### 2. Run Automated Tests

```bash
mvn test
```

- Verifies **Controller HTTP contracts & validation** (`GitHubControllerTest` using `@WebMvcTest` + `MockMvc`).
- Verifies **Service filtering, sorting, and edge-case exceptions** (`GitHubServiceTest` using JUnit 5 + Mockito).

### 3. Start the Application

```bash
mvn spring-boot:run
```

Open Swagger UI at: `http://localhost:8080/swagger-ui.html`
