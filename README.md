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

## Prerequisites

### System Requirements
- JDK 17 or newer (Java 25 toolchain used for Gradle, Java 17 bytecode target)
- Docker and Docker Compose for local infrastructure
- Stockfish executable for local worker execution (development only)

### Project Setup
```bash
# Clone the repository
git clone <repository-url>
cd chess_coach_backend

# Verify Gradle wrapper
./gradlew --version
```

## Environment Configuration

The project supports multiple deployment environments through environment variables
and Docker Compose profiles.

### Environment Files

Copy the example environment file to customize settings:

```bash
cp .env.example .env
```

### Environment Variables

#### Required for All Environments
| Variable | Purpose | Development Default | Production Requirement |
|---|---|---|---|
| `APP_ENV` | Runtime environment | `dev` | `prod` |
| `API_HOST` | API bind host | `0.0.0.0` | `0.0.0.0` or specific interface |
| `API_PORT` | API port | `8080` | Configured by deployment |

#### Security (Production Only)
| Variable | Purpose | Development Default | Production Requirement |
|---|---|---|---|
| `JWT_SECRET` | JWT signing secret | `chesscoach-development-jwt-secret-change-for-production` | Random 48+ characters |
| `DATABASE_PASSWORD` | PostgreSQL password | `chesscoach-dev-password` | Random 32+ characters |
| `RABBITMQ_PASSWORD` | RabbitMQ password | `rabbitmq-dev-password` | Random 32+ characters |

#### Database Configuration
| Variable | Purpose | Development Default | Production Default |
|---|---|---|---|
| `DATABASE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/chesscoach` | `jdbc:postgresql://postgres:5432/chesscoach` (Docker) |
| `DATABASE_USER` | PostgreSQL user | `chesscoach` | `chesscoach` |
| `POSTGRES_DB` | PostgreSQL database name | `chesscoach` | `chesscoach` |
| `POSTGRES_PORT` | PostgreSQL port | `5432` | `5432` |

#### RabbitMQ Configuration
| Variable | Purpose | Development Default | Production Default |
|---|---|---|---|
| `RABBITMQ_HOST` | RabbitMQ host | `localhost` | `rabbitmq` (Docker) |
| `RABBITMQ_WORKER_HOST` | Worker-specific RabbitMQ host | `rabbitmq` | `rabbitmq` |
| `RABBITMQ_PORT` | RabbitMQ port | `5672` | `5672` |
| `RABBITMQ_MANAGEMENT_PORT` | RabbitMQ management UI port | `15672` | `15672` |
| `RABBITMQ_USER` | RabbitMQ user | `chesscoach` | `chesscoach` |
| `RABBITMQ_QUEUE` | Analysis queue name | `analysis.jobs` | `analysis.jobs` |

#### Stockfish Configuration
| Variable | Purpose | Development Default | Production Default |
|---|---|---|---|
| `STOCKFISH_PATH` | Stockfish executable path | `stockfish` (local) | `/opt/stockfish/stockfish` (Docker) |
| `STOCKFISH_FAST_DEPTH` | Fast analysis depth | `12` | `12` |
| `STOCKFISH_DEEP_DEPTH` | Deep analysis depth | `20` | `20` |
| `STOCKFISH_THREADS` | Stockfish thread count | `1` | `1` |
| `STOCKFISH_HASH_MB` | Stockfish hash size (MB) | `128` | `128` |
| `STOCKFISH_SOURCE_URL` | Stockfish source archive URL | GitHub SF 17.1 | GitHub SF 17.1 |
| `STOCKFISH_SOURCE_SHA256` | Stockfish source SHA-256 | Verified digest | Verified digest |

## Development Setup

### Option 1: Local Services with Local Applications

This setup runs all services locally on your machine for maximum debugging capability.

#### Step 1: Install and Configure Infrastructure
```bash
# Start PostgreSQL and RabbitMQ using Docker Compose
docker compose --profile dev up -d

# Verify services are running
docker compose ps
```

#### Step 2: Install Stockfish
```bash
# Download Stockfish from official site
# https://stockfishchess.org/download/

# Extract and make executable
chmod +x stockfish

# Add to PATH or set environment variable
export STOCKFISH_PATH=/path/to/stockfish
```

#### Step 3: Start Application Services
```bash
# Start API service (terminal 1)
./gradlew :api-service:run

# Start worker service (terminal 2)
./gradlew :worker-service:run

# Start poller service (terminal 3) - optional
./gradlew :poller-service:run
```

#### Step 4: Verify Setup
```bash
# Test API health
curl http://localhost:8080/health

# Check service logs
docker compose logs -f
```

### Option 2: Docker Compose with Local Applications

This setup uses Docker for infrastructure but runs applications locally.

#### Step 1: Start Infrastructure
```bash
# Use .env.example for configuration
docker compose --env-file .env.example --profile dev up -d
```

#### Step 2: Configure Environment
```bash
# Set environment variables for local services
export DATABASE_URL=jdbc:postgresql://localhost:5432/chesscoach
export RABBITMQ_HOST=localhost
export STOCKFISH_PATH=/path/to/stockfish
```

#### Step 3: Start Applications
```bash
# Start services as in Option 1
./gradlew :api-service:run
./gradlew :worker-service:run
```

### Option 3: Full Docker Compose Development

This setup runs everything in Docker containers for consistency.

#### Step 1: Build and Start All Services
```bash
# Build and start all services including worker image
docker compose --profile dev up -d --build
```

#### Step 2: Verify Services
```bash
# Check all services are healthy
docker compose ps

# View logs
docker compose logs -f worker
docker compose logs -f api-service
```

#### Access Points
- API: `http://localhost:8080`
- RabbitMQ Management: `http://localhost:15672` (user: `chesscoach`, password: `rabbitmq-dev-password`)
- PostgreSQL: `localhost:5432`

### Stop Development Environment
```bash
# Stop services
docker compose --profile dev down

# Remove volumes and data
docker compose --profile dev down -v
```

## Production Setup

### Security Configuration

Generate secure credentials before deployment:

```bash
# Generate secure secrets
export JWT_SECRET="$(openssl rand -base64 48)"
export POSTGRES_PASSWORD="$(openssl rand -base64 32)"
export RABBITMQ_PASSWORD="$(openssl rand -base64 32)"

# Export Stockfish configuration
export STOCKFISH_SOURCE_URL="https://github.com/official-stockfish/Stockfish/archive/refs/tags/sf_17.1.tar.gz"
export STOCKFISH_SOURCE_SHA256="0cfd9396438798cc68f5c0d5fa0bb458bb8ffff7de06add841aaeace86bec1f1"
```

### Docker Compose Production Deployment

#### Step 1: Deploy Infrastructure
```bash
# Start production dependencies
docker compose -f docker-compose.prod.yml up -d

# Verify health
docker compose -f docker-compose.prod.yml ps
```

#### Step 2: Deploy Application Services
For production, deploy services in managed containers or under a process supervisor:
- Kubernetes
- Docker Swarm
- Systemd services
- Cloud platform services

Provide environment variables through the platform's secret management system.

### Multi-Platform Worker Image

Build multi-platform worker images for different architectures:

```bash
docker buildx build --platform linux/amd64,linux/arm64 \
  --build-arg STOCKFISH_SOURCE_URL="$STOCKFISH_SOURCE_URL" \
  --build-arg STOCKFISH_SOURCE_SHA256="$STOCKFISH_SOURCE_SHA256" \
  -t your-registry/chess-coach-worker:latest \
  --push .
```

### Production Considerations

**Security:**
- Never use development defaults in production
- Use strong, randomly generated passwords
- Rotate credentials regularly
- Enable TLS for database and RabbitMQ connections
- Use secret management systems (HashiCorp Vault, AWS Secrets Manager, etc.)

**Monitoring:**
- Enable health checks for all services
- Set up logging aggregation
- Monitor RabbitMQ queue depths
- Track worker processing times
- Set up alerts for connection failures

**Scaling:**
- Scale API services horizontally
- Run multiple worker instances based on queue depth
- Use connection pooling for database access
- Configure appropriate resource limits

## API Documentation

### Health Check
```http
GET /health
```

### Authentication
```http
POST /auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "change-me-123"
}
```

```http
POST /auth/token
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "change-me-123"
}
```

### Protected Endpoints
Use the returned JWT token with the `Authorization: Bearer <token>` header:

- `GET /games` - List all games
- `GET /games/:id` - Get specific game
- `GET /games/by-url?url=<encoded-url>` - Get game by Chess.com URL
- `POST /analysis/:gameId` - Queue game analysis
- `GET /analysis/:gameId` - Get game analysis results
- `GET /puzzles` - Get tactical puzzles

### Postman Collection
Import `postman/Chess-Coach.postman_collection.json` for a ready-made request collection:
1. Set collection variables
2. Run `Token` request first (saves JWT to `token` variable)
3. Run `Sync Chess.com games` to fetch games
4. Run `List games` to see available games
5. Use `Get game by ID` or `Get game by Chess.com URL` for specific games
6. Run `Queue fast game review` or `Queue deep game review` to start analysis
7. Poll `Get game analysis` to check results

For detailed API documentation, see [`API.md`](API.md).
For observability and debugging, see [`OBSERVABILITY.md`](OBSERVABILITY.md).

## Troubleshooting

### RabbitMQ Connection Issues

**Problem:** Worker cannot connect to RabbitMQ with "Connection refused" errors.

**Solution:** The RabbitMQ Java client uses AMQP URI format for reliable connections in Docker environments. The current implementation uses `amqp://user:password@host:port/%2F` format where `%2F` is the URL-encoded default virtual host.

**Verification:**
```bash
# Check RabbitMQ is running
docker compose ps rabbitmq

# Check RabbitMQ logs
docker compose logs rabbitmq

# Test connection from worker container
docker exec chess_coach_backend-worker-1 timeout 5 bash -c 'cat < /dev/null > /dev/tcp/rabbitmq/5672'
```

### Database Connection Issues

**Problem:** Services cannot connect to PostgreSQL.

**Solution:** Verify database host configuration:
- Local development: `localhost` or `127.0.0.1`
- Docker development: `postgres` (service name)
- Production: Use database service hostname or IP

**Verification:**
```bash
# Check PostgreSQL is running
docker compose ps postgres

# Test database connection
docker exec chess_coach_backend-postgres-1 pg_isready -U chesscoach
```

### Stockfish Issues

**Problem:** Worker cannot find or execute Stockfish.

**Solution:** Verify Stockfish path configuration:
- Local development: Set `STOCKFISH_PATH` to local executable
- Docker: Stockfish is built into worker image at `/opt/stockfish/stockfish`

**Verification:**
```bash
# Local: Test Stockfish directly
./stockfish version

# Docker: Check Stockfish in container
docker exec chess_coach_backend-worker-1 /opt/stockfish/stockfish version
```

### Service Health Check Failures

**Problem:** Services show as unhealthy in Docker Compose.

**Solution:** Check service logs for specific errors:
```bash
docker compose logs -f [service-name]
```

Common issues:
- Database not ready when service starts (increase health check timeouts)
- Environment variables not properly set
- Port conflicts with other services

### Port Conflicts

**Problem:** Services fail to start due to port conflicts.

**Solution:** Modify port mappings in `.env` file:
```bash
API_PORT=8081
POSTGRES_PORT=5433
RABBITMQ_PORT=5673
RABBITMQ_MANAGEMENT_PORT=15673
```

## Testing

### Unit Tests
```bash
./gradlew clean test
```

### Integration Tests
```bash
# Run integration tests (requires Docker)
RUN_INTEGRATION_TESTS=true ./gradlew test
```

### Full Build
```bash
./gradlew clean build
```

## Database Migrations

### Development
The development Compose setup automatically runs migrations on PostgreSQL startup:
```text
domain/src/main/resources/db/migration/V1__baseline.sql
```

### Production
Production deployments should run migrations as an explicit release step:
1. Run migrations before deploying new application version
2. Verify migration success
3. Deploy application instances
4. Monitor for migration-related issues

## Monitoring and Observability

### Health Checks
All services expose health endpoints:
- API: `GET /health`
- Worker: Check process status and RabbitMQ connection
- Poller: Check process status and RabbitMQ connection

### Logs
View service logs with Docker Compose:
```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f worker
docker compose logs -f api-service
```

### RabbitMQ Management UI
Access the RabbitMQ management interface:
- URL: `http://localhost:15672`
- Default credentials: `chesscoach` / `rabbitmq-dev-password` (development)
- Monitor queue depths, connection rates, and consumer activity

## License

This project is licensed under the MIT License. See [`LICENSE`](LICENSE).