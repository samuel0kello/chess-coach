package co.chesscoach.poller

import messaging.AnalysisJobPublisher
import messaging.LoggingAnalysisJobPublisher
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

private val pollerModule = module {
    single<AnalysisJobPublisher> { LoggingAnalysisJobPublisher() }
    single<PgnProviderClient> { ConfigurablePgnProviderClient() }
}

fun main() {
    startKoin {
        modules(pollerModule)
    }

    Runtime.getRuntime().addShutdownHook(Thread(::stopKoin))
    println("poller-service started")
}
