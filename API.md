# Chess Coach API

Run the API with `./gradlew :api-service:run`. Start PostgreSQL, RabbitMQ,
and the Stockfish worker with:

```bash
docker compose --env-file .env.example --profile dev up -d --build
```

Protected endpoints require `Authorization: Bearer <jwt>`.

## Endpoints

- `GET /health` - liveness check.
- `POST /auth/register` - register a user.
- `POST /auth/token` - obtain a JWT.
- `POST /games/sync` - import the latest three Chess.com archive months.
- `GET /games` - list up to 50 games belonging to the authenticated user.
- `GET /games/:id` - retrieve a game by its stored ID.
- `GET /games/by-url?url=<encoded-url>` - retrieve a game stored under a full Chess.com URL.
- `POST /analysis/:gameId` - queue a FAST or DEEP review.
- `GET /analysis/:gameId` - retrieve persisted analysis results.
- `GET /puzzles` - retrieve generated puzzles.

## Game review

First obtain a token and sync games. Copy an ID from `GET /games`, then queue
a review:

```http
POST /analysis/170979798774
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "tier": "FAST"
}
```

The API returns `202 Accepted`:

```json
{
  "gameId": "170979798774",
  "tier": "FAST",
  "status": "queued"
}
```

Poll the result:

```http
GET /analysis/170979798774
Authorization: Bearer <jwt>
```

The endpoint returns `[]` while the worker is processing. The current worker
job analyzes the initial position for the selected game. Full move-by-move PGN
review and automatic puzzle generation are not yet exposed.

## Postman

Import `postman/Chess-Coach.postman_collection.json`. Set `email`, `password`,
and `chessComUsername`, then run `Token`. The token test script stores the JWT
in the `token` collection variable.

Run requests in this order:

1. `Token`
2. `Sync Chess.com games`
3. `List games`
4. Set `gameId` from the list response
5. `Queue fast game review` or `Queue deep game review`
6. Wait for the worker, then run `Get game analysis`

`List puzzles` is included for generated puzzle records and currently returns
an empty array until puzzle generation is implemented.

Production requires `APP_ENV=prod` and a random `JWT_SECRET` of at least 32
characters. Never commit `.env` or production credentials.
