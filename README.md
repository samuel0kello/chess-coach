# Chess Coach Backend

Chess Coach Backend is a Kotlin/JVM backend for collecting chess games,
analyzing positions with Stockfish, and exposing coaching data such as move
evaluations and tactical puzzles.

The project is organized as a modular service-oriented backend built with
Vert.x, Koin, Exposed, PostgreSQL, RabbitMQ, Kotlin serialization, and the
Stockfish UCI engine.

## Architecture

```text
                         +-------------------+
                         |   Chess provider  |
                         | Chess.com/Lichess |
                         +---------+---------+
                                   |
                                   v
                         +-------------------+
                         |   poller-service  |
                         | Fetch PGN games   |
                         +---------+---------+
                                   |
                                   v
                         +-------------------+
                         |     RabbitMQ      |
                         |  Analysis jobs    |
                         +---------+---------+
                                   |
                                   v
                         +-------------------+
                         |   worker-service  |
                         | Stockfish + UCI   |
                         +---------+---------+
                                   |
                                   v
                         +-------------------+
                         |    PostgreSQL     |
                         | Games/evaluations |
                         +---------+---------+
                                   ^
                                   |
                         +---------+---------+
                         |    api-service    |
                         | Vert.x HTTP/JWT   |
                         +-------------------+
```

### Modules

- **`domain`** — Shared domain contracts, serialized messages, Exposed table
  definitions, repositories, database configuration, and messaging primitives.
- **`api-service`** — Vert.x HTTP API with health, authentication, games,
  analysis, and puzzle endpoints.
- **`poller-service`** — Background service boundary for fetching PGN games from
  chess providers and publishing analysis jobs.
- **`worker-service`** — RabbitMQ consumer that runs Stockfish analysis and
  persists results.
- **`buildLogic`** — Convention plugins for shared Kotlin/JVM, Vert.x,
  serialization, and Koin build configuration.

## Technology stack

- Kotlin/JVM
- Gradle Kotlin DSL and centralized version catalog
- Vert.x
- Koin
- Exposed
- PostgreSQL
- RabbitMQ
- Kotlin serialization
- Stockfish through the UCI protocol
- JWT authentication
- Docker Compose

### Stockfish distribution

Production worker images build Stockfish from a pinned official source archive,
verify its SHA-256 digest, and include the executable and corresponding source
archive in the image. Stockfish remains a separate GPLv3 component; the
application code remains MIT-licensed. See
[`third-party/stockfish/SOURCE-OFFER.txt`](third-party/stockfish/SOURCE-OFFER.txt)
for source and license details.

## Requirements

- JDK 17 or newer
- Gradle wrapper (`./gradlew`)
- Docker and Docker Compose for local infrastructure
- Stockfish executable for local worker execution

The project uses a Java 25 toolchain for Gradle toolchain selection and targets
Java 17 bytecode for compatibility.

## Configuration

Copy the example environment file when running the API and worker locally:

```bash
cp .env.example .env
```

The development Compose file contains the same safe local-only defaults, so
this also works without creating `.env`:

```bash
docker compose --profile dev up -d
```

If you want Compose to load the example file explicitly, use:

```bash
docker compose --env-file .env.example --profile dev up -d
```

Update the values as needed. Important settings include:

| Variable | Purpose | Development default |
|---|---|---|
| `APP_ENV` | Runtime environment | `dev` |
| `API_HOST` | API bind host | `0.0.0.0` |
| `API_PORT` | API port | `8080` |
| `JWT_SECRET` | JWT signing secret | Replace before use |
| `DATABASE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/chesscoach` |
| `DATABASE_USER` | PostgreSQL user | `chesscoach` |
| `DATABASE_PASSWORD` | PostgreSQL password | Replace before use |
| `RABBITMQ_HOST` | RabbitMQ host | `localhost` |
| `RABBITMQ_PORT` | RabbitMQ port | `5672` |
| `RABBITMQ_QUEUE` | Analysis queue | `analysis.jobs` |
| `STOCKFISH_PATH` | Stockfish executable path | `stockfish` |
| `STOCKFISH_FAST_DEPTH` | Fast analysis depth | `12` |
| `STOCKFISH_DEEP_DEPTH` | Deep analysis depth | `20` |
| `STOCKFISH_THREADS` | Stockfish thread count | `1` |
| `STOCKFISH_HASH_MB` | Stockfish hash size | `128` |

Production requires `APP_ENV=prod`, explicit database and RabbitMQ passwords,
and a non-default JWT secret of at least 32 characters. Never use the
development defaults in production.

## Run in development

Start PostgreSQL and RabbitMQ:

```bash
docker compose --profile dev up -d
```

Install Stockfish from the official download page:

<https://stockfishchess.org/download/>

Then either put the executable on `PATH` or configure it explicitly:

```bash
export STOCKFISH_PATH=/absolute/path/to/stockfish
```

Start the API:

```bash
./gradlew :api-service:run
```

Start the worker in another terminal:

```bash
./gradlew :worker-service:run
```

Start the poller in another terminal:

```bash
./gradlew :poller-service:run
```

The development Compose profile does not build the production worker image, so
the worker can use a locally installed Stockfish executable.

The API is available at:

```text
http://localhost:8080
```

Stop development infrastructure with:

```bash
docker compose --profile dev down
```

Add `-v` to remove the PostgreSQL volume and its data.

## Run in production

Use strong, externally managed credentials in production:

```bash
export APP_ENV=prod
export JWT_SECRET="$(openssl rand -base64 48)"
export POSTGRES_PASSWORD="$(openssl rand -base64 32)"
export RABBITMQ_PASSWORD="$(openssl rand -base64 32)"
export STOCKFISH_SOURCE_URL="https://github.com/official-stockfish/Stockfish/archive/refs/tags/sf_17.1.tar.gz"
export STOCKFISH_SOURCE_SHA256="0cfd9396438798cc68f5c0d5fa0bb458bb8ffff7de06add841aaeace86bec1f1"
```

Start the production dependencies:

```bash
docker compose -f docker-compose.prod.yml up -d
```

The production worker image contains Stockfish at
`/opt/stockfish/stockfish`. Build a multi-platform image with
`docker buildx build --platform linux/amd64,linux/arm64`, supplying a verified
source URL and SHA-256 digest.

For production deployments, run the services in managed containers or under a
process supervisor and provide the environment variables through the platform's
secret-management system. Do not commit `.env` files or credentials.

## API

### Health check

```http
GET /health
```

### Register

```http
POST /auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "change-me-123"
}
```

### Get a token

```http
POST /auth/token
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "change-me-123"
}
```

Use the returned token for protected endpoints:

```http
Authorization: Bearer <jwt-token>
```

Available protected endpoints include:

- `GET /games`
- `GET /games/:id`
- `GET /games/by-url?url=<encoded-chess-com-url>`
- `POST /analysis/:gameId`
- `GET /analysis/:gameId`
- `GET /puzzles`

Import `postman/Chess-Coach.postman_collection.json` into Postman for a
ready-made request collection. Set the collection variables, run `Token`
first, and its test script will save the JWT to the `token` variable. Then run
`Sync Chess.com games` followed by `List games`. Use `Get game by ID` with an
ID from the list response, or `Get game by Chess.com URL` for full Chess.com
URLs. To start a review, run `Queue fast game review` or `Queue deep game
review`, then poll `Get game analysis` after the worker completes. Start the
development worker with:

```bash
docker compose --env-file .env.example --profile dev up -d --build
```

More endpoint details are available in [`API.md`](API.md).

For request tracing and Docker log commands, see
[`OBSERVABILITY.md`](OBSERVABILITY.md).

## Testing

Run the complete unit and module test suite:

```bash
./gradlew clean test
```

Run a complete build:

```bash
./gradlew clean build
```

PostgreSQL integration tests can be enabled when Docker is available:

```bash
RUN_INTEGRATION_TESTS=true ./gradlew test
```

## Database migrations

The initial SQL migration is located at:

```text
domain/src/main/resources/db/migration/V1__baseline.sql
```

The development Compose setup mounts migration resources into PostgreSQL's
initialization directory. Production deployments should run migrations as an
explicit release step before starting application instances.

## License

This project is licensed under the MIT License. See [`LICENSE`](LICENSE).
