package com.cosmos.stealth.services.reddit.di

import com.cosmos.stealth.core.common.di.DispatchersModule
import com.cosmos.stealth.core.common.di.DispatchersModule.Qualifier.IO_DISPATCHER_QUALIFIER
import com.cosmos.stealth.core.common.util.TimeValue
import com.cosmos.stealth.core.data.di.DataModule
import com.cosmos.stealth.core.network.data.converter.MoshiContentConverter
import com.cosmos.stealth.core.network.data.converter.moshi
import com.cosmos.stealth.core.network.di.NetworkModule
import com.cosmos.stealth.core.network.util.UrlSubstitutor
import com.cosmos.stealth.core.network.util.extension.cache
import com.cosmos.stealth.core.network.util.extension.forwardProxy
import com.cosmos.stealth.services.reddit.data.adapter.EditedAdapter
import com.cosmos.stealth.services.reddit.data.adapter.MediaMetadataAdapter
import com.cosmos.stealth.services.reddit.data.adapter.NullToEmptyStringAdapter
import com.cosmos.stealth.services.reddit.data.adapter.RepliesAdapter
import com.cosmos.stealth.services.reddit.data.model.AboutChild
import com.cosmos.stealth.services.reddit.data.model.AboutUserChild
import com.cosmos.stealth.services.reddit.data.model.Child
import com.cosmos.stealth.services.reddit.data.model.ChildType
import com.cosmos.stealth.services.reddit.data.model.CommentChild
import com.cosmos.stealth.services.reddit.data.model.MoreChild
import com.cosmos.stealth.services.reddit.data.model.PostChild
import com.cosmos.stealth.services.reddit.data.remote.JsonInterceptor
import com.cosmos.stealth.services.reddit.data.remote.RawJsonInterceptor
import com.cosmos.stealth.services.reddit.data.remote.RedditBrowserHeadersInterceptor
import com.cosmos.stealth.services.reddit.data.remote.RedditCookieInitializationInterceptor
import com.cosmos.stealth.services.reddit.data.remote.RedditCookieJar
import com.cosmos.stealth.services.reddit.data.remote.api.DataRedditApi
import com.cosmos.stealth.services.reddit.data.remote.api.RedditApi
import com.cosmos.stealth.services.reddit.data.remote.api.ScrapRedditApi
import com.cosmos.stealth.services.reddit.di.RedditModule.Qualifier.GENERIC_QUALIFIER
import com.cosmos.stealth.services.reddit.di.RedditModule.Qualifier.REDDIT_QUALIFIER
import com.cosmos.stealth.services.reddit.di.RedditModule.Qualifier.REDDIT_SCRAP_QUALIFIER
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
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

@Module(includes = [DispatchersModule::class, DataModule::class, NetworkModule::class])
@ComponentScan("com.cosmos.stealth.services.reddit")
class RedditModule {

    @Suppress("MagicNumber", "VariableNaming")
    private val TIMEOUT = TimeValue<Long>(60, TimeUnit.SECONDS)

    object Qualifier {
        const val REDDIT_QUALIFIER = "reddit"
        const val REDDIT_SCRAP_QUALIFIER = "reddit_scrap"
        const val GENERIC_QUALIFIER = "generic"
    }

    @Single
    @Named(REDDIT_QUALIFIER)
    fun provideRedditMoshi(): Moshi {
        return Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(Child::class.java, "kind")
                    .withSubtype(CommentChild::class.java, ChildType.t1.name)
                    .withSubtype(AboutUserChild::class.java, ChildType.t2.name)
                    .withSubtype(PostChild::class.java, ChildType.t3.name)
                    .withSubtype(AboutChild::class.java, ChildType.t5.name)
                    .withSubtype(MoreChild::class.java, ChildType.more.name)
            )
            .add(MediaMetadataAdapter.Factory)
            .add(RepliesAdapter())
            .add(EditedAdapter())
            .add(NullToEmptyStringAdapter())
            .build()
    }

    @Single
    @Named(GENERIC_QUALIFIER)
    fun provideGenericMoshi(): Moshi {
        return Moshi.Builder().build()
    }

    @Single
    @Named(REDDIT_QUALIFIER)
    fun provideRedditMoshiContentConverter(
        @Named(REDDIT_QUALIFIER) moshi: Moshi,
        @Named(IO_DISPATCHER_QUALIFIER) ioDispatcher: CoroutineDispatcher
    ): MoshiContentConverter {
        return MoshiContentConverter(moshi, ioDispatcher)
    }

    @Single
    fun provideRedditCookieJar(): RedditCookieJar {
        return RedditCookieJar()
    }

    @Single
    @Named(REDDIT_QUALIFIER)
    fun provideRedditOkHttpClient(cookieJar: RedditCookieJar): OkHttpClient {
        return OkHttpClient.Builder()
            .forwardProxy("reddit")
            .addInterceptor(RedditBrowserHeadersInterceptor())
            .addInterceptor(RawJsonInterceptor())
            .addInterceptor(JsonInterceptor())
            .addInterceptor(RedditCookieInitializationInterceptor(cookieJar))
            .cache(REDDIT_QUALIFIER)
            .connectTimeout(TIMEOUT.value, TIMEOUT.unit)
            .readTimeout(TIMEOUT.value, TIMEOUT.unit)
            .writeTimeout(TIMEOUT.value, TIMEOUT.unit)
            .cookieJar(cookieJar)
            .build()
    }

    @Single
    @Named(REDDIT_SCRAP_QUALIFIER)
    fun provideRedditScrapOkHttpClient(cookieJar: RedditCookieJar): OkHttpClient {
        return OkHttpClient.Builder()
            .forwardProxy("reddit")
            .addInterceptor(RedditBrowserHeadersInterceptor())
            .cache(REDDIT_SCRAP_QUALIFIER)
            .connectTimeout(TIMEOUT.value, TIMEOUT.unit)
            .readTimeout(TIMEOUT.value, TIMEOUT.unit)
            .writeTimeout(TIMEOUT.value, TIMEOUT.unit)
            .cookieJar(cookieJar)
            .build()
    }

    @Single
    @Named(REDDIT_QUALIFIER)
    fun provideRedditHttpClient(
        @Named(REDDIT_QUALIFIER) okHttpClient: OkHttpClient,
        @Named(REDDIT_QUALIFIER) moshiContentConverter: MoshiContentConverter
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
    @Named(REDDIT_SCRAP_QUALIFIER)
    fun provideRedditScrapHttpClient(@Named(REDDIT_SCRAP_QUALIFIER) okHttpClient: OkHttpClient): HttpClient {
        return HttpClient(OkHttp) {
            BrowserUserAgent()

            engine {
                preconfigured = okHttpClient
            }

            expectSuccess = true
        }
    }


    @Single
    @Named(REDDIT_QUALIFIER)
    internal fun provideDataRedditApi(
        @Named(REDDIT_QUALIFIER) httpClient: HttpClient,
        urlSubstitutor: UrlSubstitutor
    ): RedditApi {
        return DataRedditApi(httpClient, urlSubstitutor)
    }

    @Single
    @Named(REDDIT_SCRAP_QUALIFIER)
    internal fun provideScrapRedditApi(
        @Named(REDDIT_SCRAP_QUALIFIER) httpClient: HttpClient,
        urlSubstitutor: UrlSubstitutor,
        @Named(IO_DISPATCHER_QUALIFIER) ioDispatcher: CoroutineDispatcher
    ): RedditApi {
        return ScrapRedditApi(httpClient, urlSubstitutor, ioDispatcher)
    }
}
