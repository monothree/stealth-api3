package com.cosmos.stealth.services.teddit.di

import com.cosmos.stealth.core.common.di.DispatchersModule
import com.cosmos.stealth.core.common.util.TimeValue
import com.cosmos.stealth.core.network.di.NetworkModule
import com.cosmos.stealth.core.network.util.extension.cache
import com.cosmos.stealth.services.reddit.di.RedditModule
import com.cosmos.stealth.services.teddit.data.remote.api.RedlibApi
import com.cosmos.stealth.services.teddit.data.scraper.redlib.RedlibDeepReplyRegistry
import com.cosmos.stealth.services.teddit.di.TedditModule.Qualifier.TEDDIT_QUALIFIER
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import java.util.concurrent.TimeUnit

@Module(includes = [DispatchersModule::class, RedditModule::class, NetworkModule::class])
@ComponentScan("com.cosmos.stealth.services.teddit")
class TedditModule {

    @Suppress("MagicNumber", "VariableNaming")
    private val TIMEOUT = TimeValue<Long>(60, TimeUnit.SECONDS)

    object Qualifier {
        const val TEDDIT_QUALIFIER = "teddit"
    }

    @Single
    fun provideDeepReplyRegistry(): RedlibDeepReplyRegistry {
        return RedlibDeepReplyRegistry()
    }

    @Single
    @Named(TEDDIT_QUALIFIER)
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .cache(TEDDIT_QUALIFIER)
            .connectTimeout(TIMEOUT.value, TIMEOUT.unit)
            .readTimeout(TIMEOUT.value, TIMEOUT.unit)
            .writeTimeout(TIMEOUT.value, TIMEOUT.unit)
            .build()
    }

    @Single
    @Named(TEDDIT_QUALIFIER)
    fun provideHttpClient(
        @Named(TEDDIT_QUALIFIER) okHttpClient: OkHttpClient
    ): HttpClient {
        return HttpClient(OkHttp) {
            engine {
                preconfigured = okHttpClient
            }

            expectSuccess = true
        }
    }

    @Single
    internal fun provideRedlibApi(
        @Named(TEDDIT_QUALIFIER) httpClient: HttpClient
    ): RedlibApi {
        return RedlibApi(httpClient)
    }
}
