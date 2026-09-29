package com.cosmos.stealth.services.lemmy.di

import com.cosmos.stealth.core.common.di.DispatchersModule
import com.cosmos.stealth.core.common.di.DispatchersModule.Qualifier.DEFAULT_DISPATCHER_QUALIFIER
import com.cosmos.stealth.core.common.di.DispatchersModule.Qualifier.IO_DISPATCHER_QUALIFIER
import com.cosmos.stealth.core.common.util.MarkdownParser
import com.cosmos.stealth.core.common.util.TimeValue
import com.cosmos.stealth.core.network.data.converter.MoshiContentConverter
import com.cosmos.stealth.core.network.data.converter.moshi
import com.cosmos.stealth.core.network.di.NetworkModule
import com.cosmos.stealth.core.network.util.extension.cache
import com.cosmos.stealth.core.network.util.extension.forwardProxy
import com.cosmos.stealth.services.lemmy.di.LemmyModule.Qualifier.LEMMY_QUALIFIER
import com.squareup.moshi.Moshi
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import java.util.concurrent.TimeUnit

@Module(includes = [DispatchersModule::class, NetworkModule::class])
@ComponentScan("com.cosmos.stealth.services.lemmy")
class LemmyModule {

    @Suppress("MagicNumber", "VariableNaming")
    private val TIMEOUT = TimeValue<Long>(60, TimeUnit.SECONDS)

    object Qualifier {
        const val LEMMY_QUALIFIER = "lemmy"
    }

    @Single
    @Named(LEMMY_QUALIFIER)
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .build()
    }

    @Single
    @Named(LEMMY_QUALIFIER)
    fun provideMoshiConverter(
        @Named(LEMMY_QUALIFIER) moshi: Moshi,
        @Named(IO_DISPATCHER_QUALIFIER) ioDispatcher: CoroutineDispatcher
    ): MoshiContentConverter {
        return MoshiContentConverter(moshi, ioDispatcher)
    }

    @Single
    @Named(LEMMY_QUALIFIER)
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .forwardProxy("lemmy")
            .cache(LEMMY_QUALIFIER)
            .connectTimeout(TIMEOUT.value, TIMEOUT.unit)
            .readTimeout(TIMEOUT.value, TIMEOUT.unit)
            .writeTimeout(TIMEOUT.value, TIMEOUT.unit)
            .build()
    }

    @Single
    @Named(LEMMY_QUALIFIER)
    fun provideHttpClient(
        @Named(LEMMY_QUALIFIER) okHttpClient: OkHttpClient,
        @Named(LEMMY_QUALIFIER) moshiContentConverter: MoshiContentConverter
    ): HttpClient {
        return HttpClient(OkHttp) {
            BrowserUserAgent()

            install(ContentNegotiation) {
                moshi(moshiContentConverter)
            }

            engine {
                preconfigured = okHttpClient
            }

            expectSuccess = true
        }
    }

    @Single
    @Named(LEMMY_QUALIFIER)
    fun provideMarkdownParser(
        @Named(DEFAULT_DISPATCHER_QUALIFIER) defaultDispatcher: CoroutineDispatcher
    ): MarkdownParser {
        return MarkdownParser(defaultDispatcher = defaultDispatcher)
    }
}
