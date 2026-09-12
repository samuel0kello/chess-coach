package co.chesscoach

import domain.*
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.JWTOptions
import io.vertx.ext.auth.PubSecKeyOptions
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.HttpException
import io.vertx.ext.web.handler.JWTAuthHandler
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import messaging.AnalysisJobPublisher
import messaging.RabbitAnalysisJobPublisher
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.module
import persistence.*
import schema.*
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class MainVerticle : AbstractVerticle() {
    private lateinit var jwt: JWTAuth
    private lateinit var koin: KoinApplication
    private lateinit var config: ApiConfig

    override fun start(startPromise: Promise<Void>) {
        try {
            config = ApiConfig()
            val database = DatabaseConfig().connect()
            transaction(database) {
                SchemaUtils.create(Users, ChessAccounts, Games, MoveEvaluations, Puzzles)
                exec("ALTER TABLE games ALTER COLUMN opening_eco TYPE varchar(255)")
            }
            koin = startKoin { modules(apiModule(database)) }
            jwt =
                JWTAuth.create(
                    vertx,
                    JWTAuthOptions().addPubSecKey(
                        PubSecKeyOptions().setAlgorithm("HS256").setBuffer(config.jwtSecret),
                    ),
                )
            val router =
                Router.router(vertx).apply {
                    route().handler { ctx ->
                        val requestId = REQUEST_COUNTER.incrementAndGet().toString()
                        ctx.put("requestId", requestId)
                        val startedAt = System.nanoTime()
                        println(
                            "[api] request.start id=$requestId method=${ctx.request().method()} path=${ctx.request().path()} query=${ctx.request().query() ?: ""}",
                        )
                        ctx.response().endHandler {
                            val durationMs = (System.nanoTime() - startedAt) / 1_000_000
                            println("[api] request.end id=$requestId status=${ctx.response().statusCode} durationMs=$durationMs")
                        }
                        ctx.next()
                    }
                    route().handler(BodyHandler.create())
                    route().failureHandler { context ->
                        val error = context.failure()
                        val httpError = error as? HttpException
                        val status = httpError?.statusCode ?: 500
                        val message = if (status == 401) "unauthorized" else "request failed"
                        println("[api] request failure path=${context.request().path()} status=$status error=${error?.message}")
                        if (status >= 400) {
                            error?.printStackTrace()
                        }
                        if (!context.response().ended()) {
                            writeError(context, status, message, if (status >= 500) error else null)
                        }
                    }
                    get("/health").handler { it.json(JsonObject().put("status", "ok").put("service", "api")) }
                    post("/auth/register").handler(::register)
                    post("/auth/token").handler(::token)
                    post("/games/sync").handler(JWTAuthHandler.create(jwt)).handler(::syncGames)
                    get("/games").handler(JWTAuthHandler.create(jwt)).handler { ctx ->
                        val userId = ctx.user().principal().getString("sub")
                        val account = koin.koin.get<ChessAccountRepository>().findByUserId(userId)
                        if (account == null) {
                            println("[api] GET /games userId=$userId account=none games=0")
                            ctx.jsonEncoded(emptyList<Game>())
                        } else {
                            val games = koin.koin.get<GameRepository>().list(account.id)
                            println("[api] GET /games userId=$userId accountId=${account.id} games=${games.size}")
                            ctx.jsonEncoded(games)
                        }
                    }
                    get("/games/by-url").handler(JWTAuthHandler.create(jwt)).handler { ctx ->
                        val id = ctx.request().getParam("url")
                        if (id.isNullOrBlank()) {
                            writeError(ctx, 400, "url query parameter is required")
                        } else {
                            println("[api] GET /games/by-url lookup=$id")
                            koin.koin.get<GameRepository>().find(id)?.let { game ->
                                ctx.jsonEncoded(game)
                            } ?: run {
                                println("[api] GET /games/by-url not-found lookup=$id")
                                ctx.response().setStatusCode(404).end(JsonObject().put("error", "game not found").encode())
                            }
                        }
                    }
                    get("/games/:id").handler(JWTAuthHandler.create(jwt)).handler { ctx ->
                        val requestedId = ctx.pathParam("id")
                        println("[api] GET /games/:id lookup=$requestedId")
                        koin.koin.get<GameRepository>().find(requestedId)?.let { game ->
                            ctx.jsonEncoded(game)
                        }
                            ?: run {
                                println("[api] GET /games/:id not-found lookup=$requestedId")
                                ctx.response().setStatusCode(404).end(JsonObject().put("error", "game not found").encode())
                            }
                    }
                    get("/analysis/:gameId").handler(JWTAuthHandler.create(jwt)).handler { ctx ->
                        ctx.jsonEncoded(koin.koin.get<AnalysisRepository>().findByGame(ctx.pathParam("gameId")))
                    }
                    post("/analysis/:gameId").handler(JWTAuthHandler.create(jwt)).handler(::requestAnalysis)
                    get("/puzzles").handler(JWTAuthHandler.create(jwt)).handler {
                        it.jsonEncoded(koin.koin.get<PuzzleRepository>().list())
                    }
                }
            vertx
                .createHttpServer()
                .requestHandler(router)
                .listen(config.port, config.host)
                .onSuccess { startPromise.complete() }
                .onFailure(startPromise::fail)
        } catch (error: Throwable) {
            startPromise.fail(error)
        }
    }

    private inline fun <reified T> RoutingContext.jsonEncoded(value: T) {
        response()
            .putHeader("content-type", "application/json")
            .end(Json.encodeToString(value))
    }

    private fun syncGames(ctx: RoutingContext) {
        val userId = ctx.user().principal().getString("sub")
        val username = ctx.bodyOrNull()?.getString("username")?.trim()
        if (username.isNullOrBlank()) {
            ctx
                .response()
                .setStatusCode(400)
                .end(JsonObject().put("error", "username is required").encode())
            return
        }

        println("[api] POST /games/sync userId=$userId username=$username")
        val client = koin.koin.get<ChessComClient>()
        vertx
            .executeBlocking {
                val games = client.recentGames(username)
                try {
                    val accountRepository = koin.koin.get<ChessAccountRepository>()
                    val account =
                        accountRepository.findByUserId(userId)
                            ?: ChessAccount(UUID.randomUUID().toString(), userId, username)
                                .also(accountRepository::save)
                    val gameRepository = koin.koin.get<GameRepository>()
                    games.forEach { gameRepository.save(it.copy(accountId = account.id)) }
                } catch (error: Throwable) {
                    throw SyncPersistenceFailure(error)
                }
                SyncResult(username, games.size)
            }.onSuccess { result ->
                println("[api] Chess.com sync complete userId=$userId username=${result.username} imported=${result.imported}")
                ctx.json(
                    JsonObject()
                        .put("username", result.username)
                        .put("imported", result.imported),
                )
            }.onFailure { error ->
                val persistenceFailure = error as? SyncPersistenceFailure
                val cause = persistenceFailure?.cause ?: error
                val status = if (persistenceFailure != null) 500 else 502
                val message = if (persistenceFailure != null) "games could not be saved" else "Chess.com sync failed"
                println("[api] Chess.com sync failed userId=$userId username=$username status=$status error=${cause.message}")
                error.printStackTrace()
                writeError(ctx, status, message, cause)
            }
    }

    private fun requestAnalysis(ctx: io.vertx.ext.web.RoutingContext) {
        val gameId = ctx.pathParam("gameId")
        val tier =
            when (ctx.bodyOrNull()?.getString("tier", "FAST")?.uppercase()) {
                "FAST" -> {
                    "FAST"
                }

                "DEEP" -> {
                    "DEEP"
                }

                else -> {
                    writeError(ctx, 400, "tier must be FAST or DEEP")
                    return
                }
            }
        if (koin.koin.get<GameRepository>().find(gameId) == null) {
            writeError(ctx, 404, "game not found")
            return
        }
        println("[api] POST /analysis/$gameId tier=$tier")
        vertx
            .executeBlocking {
                runBlocking {
                    if (tier == "DEEP") {
                        koin.koin.get<AnalysisJobPublisher>().publishDeepAnalysis(gameId)
                    } else {
                        koin.koin.get<AnalysisJobPublisher>().publishFastAnalysis(gameId)
                    }
                }
            }.onSuccess {
                ctx.response().setStatusCode(202).end(
                    JsonObject()
                        .put("gameId", gameId)
                        .put("tier", tier)
                        .put("status", "queued")
                        .encode(),
                )
            }.onFailure { error ->
                val cause = rootCause(error)
                println("[api] analysis queue failed gameId=$gameId error=${cause.message}")
                error.printStackTrace()
                writeError(ctx, 503, "analysis worker unavailable", cause)
            }
    }

    private data class SyncResult(
        val username: String,
        val imported: Int,
    )

    private class SyncPersistenceFailure(
        cause: Throwable,
    ) : RuntimeException(cause)

    private fun writeError(
        ctx: io.vertx.ext.web.RoutingContext,
        status: Int,
        message: String,
        cause: Throwable? = null,
    ) {
        if (ctx.response().ended()) return
        val detail = cause?.message?.takeIf { it.isNotBlank() } ?: cause?.javaClass?.simpleName
        val body = JsonObject().put("error", message)
        if (detail != null) body.put("detail", detail)
        ctx
            .response()
            .setStatusCode(status)
            .putHeader("content-type", "application/json")
            .end(body.encode())
    }

    private fun register(ctx: io.vertx.ext.web.RoutingContext) {
        val body = ctx.bodyOrNull()
        val email = body?.getString("email")?.trim()?.lowercase()
        val password = body?.getString("password")
        if (email.isNullOrBlank() || password.isNullOrBlank() || password.length < 8 || !email.contains("@")) {
            println("[api] POST /auth/register rejected validation")
            ctx.response().setStatusCode(400).end(JsonObject().put("error", "email and password (8+ characters) are required").encode())
            return
        }
        val users = koin.koin.get<UserRepository>()
        if (users.findByEmail(email) != null) {
            println("[api] POST /auth/register conflict email=$email")
            ctx.response().setStatusCode(409).end(JsonObject().put("error", "email already registered").encode())
            return
        }
        users.save(UserAccount(UUID.randomUUID().toString(), email, hash(password)))
        println("[api] POST /auth/register success email=$email")
        ctx.response().setStatusCode(201).end(JsonObject().put("email", email).encode())
    }

    private fun token(ctx: io.vertx.ext.web.RoutingContext) {
        val body = ctx.bodyOrNull()
        val email = body?.getString("email")?.trim()?.lowercase()
        val password = body?.getString("password")
        val user = if (email != null && password != null) koin.koin.get<UserRepository>().findByEmail(email) else null
        if (user == null || user.passwordHash != hash(password!!)) {
            println("[api] POST /auth/token failed email=$email")
            ctx.response().setStatusCode(401).end(JsonObject().put("error", "invalid credentials").encode())
            return
        }
        println("[api] POST /auth/token success userId=${user.id}")
        val token =
            jwt.generateToken(
                JsonObject().put("sub", user.id).put("email", user.email),
                JWTOptions().setExpiresInMinutes(config.jwtExpiryMinutes),
            )
        ctx.json(JsonObject().put("token", token).put("tokenType", "Bearer").put("expiresInMinutes", config.jwtExpiryMinutes))
    }

    override fun stop(stopPromise: Promise<Void>) {
        if (::koin.isInitialized) {
            (koin.koin.getOrNull<AnalysisJobPublisher>() as? AutoCloseable)?.close()
            koin.close()
        }
        stopPromise.complete()
    }
}

private fun io.vertx.ext.web.RoutingContext.bodyOrNull(): JsonObject? = runCatching { body().asJsonObject() }.getOrNull()

private fun hash(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

private fun rootCause(error: Throwable): Throwable {
    var cause = error
    while (cause.cause != null && cause.cause !== cause) {
        cause = cause.cause!!
    }
    return cause
}

private fun apiModule(database: org.jetbrains.exposed.sql.Database) =
    module {
        single { database }
        single<GameRepository> { ExposedGameRepository() }
        single<AnalysisRepository> { ExposedAnalysisRepository() }
        single<PuzzleRepository> { ExposedPuzzleRepository() }
        single<UserRepository> { ExposedUserRepository() }
        single<ChessAccountRepository> { ExposedChessAccountRepository() }
        single { ChessComClient() }
        single<AnalysisJobPublisher> { RabbitAnalysisJobPublisher() }
    }

private val REQUEST_COUNTER = AtomicLong()
