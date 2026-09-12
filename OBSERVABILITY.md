# Local request and container logs

The API started with `./gradlew :api-service:run` writes request logs to the
Gradle terminal. Every request has a start and end entry:

```text
[api] request.start id=12 method=GET path=/games/170979798774 query=
[api] GET /games/:id lookup=170979798774
[api] GET /games/:id not-found lookup=170979798774
[api] request.end id=12 status=404 durationMs=18
```

If a Chess.com game was stored with its full URL, use the numeric suffix or the
URL lookup endpoint:

```bash
curl --get 'http://localhost:8080/games/by-url' \
  --data-urlencode 'url=https://www.chess.com/game/live/170979798774' \
  --header 'Authorization: Bearer <token>'
```

The repository also accepts the numeric suffix:

```bash
curl 'http://localhost:8080/games/170979798774' \
  --header 'Authorization: Bearer <token>'
```

Start the Docker services:

```bash
docker compose --env-file .env.example --profile dev up -d --build
```

Follow all service logs:

```bash
docker compose --env-file .env.example --profile dev logs -f --tail=100
```

Follow only analysis worker logs:

```bash
docker compose --env-file .env.example --profile dev logs -f --tail=100 worker
```

The host-run API uses `localhost` for RabbitMQ. The worker uses the Compose
service name `rabbitmq` for its direct in-network connection. If the worker
must reach a broker outside this Compose project, set `RABBITMQ_WORKER_HOST`
to that externally reachable hostname in `.env`.

The API logs RabbitMQ connection configuration without exposing the password:

```text
[rabbitmq] connecting host=localhost port=5672 user=chesscoach queue=analysis.jobs
[rabbitmq] connected host=localhost port=5672 queue=analysis.jobs
```

If RabbitMQ logs `PLAIN login refused` for `chesscoach`, the container was
initialized earlier with a different password. Compose does not change
credentials in an existing RabbitMQ data directory. Reset the development
password explicitly:

```bash
docker exec chess_coach_backend-rabbitmq-1 \
  rabbitmqctl change_password chesscoach rabbitmq-dev-password
docker exec chess_coach_backend-rabbitmq-1 \
  rabbitmqctl authenticate_user chesscoach rabbitmq-dev-password
```

Restart the API and worker after changing credentials:

```bash
./gradlew :api-service:run
docker compose --env-file .env.example --profile dev up -d --build worker
```

The worker logs analysis lifecycle events:

```text
[worker] analysis.start ...
[worker] analysis.complete ...
```

Because the API is currently run on the host with Gradle, its logs appear in
the Gradle terminal, not in `docker logs`. PostgreSQL and RabbitMQ container
logs show infrastructure events; application request logs are emitted by the
API and worker processes.
