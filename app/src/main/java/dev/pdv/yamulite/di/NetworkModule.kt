package dev.pdv.yamulite.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.pdv.yamulite.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    // Shared across the auth and music OkHttpClient instances so both API calls and Coil's
    // image fetches reuse one connection pool and one dispatcher thread pool instead of two.
    @Provides @Singleton
    fun provideConnectionPool(): ConnectionPool = ConnectionPool()

    @Provides @Singleton
    fun provideOkHttpDispatcher(): Dispatcher = Dispatcher()

    @Provides @Singleton
    fun provideOkHttp(
        @ApplicationContext context: Context,
        connectionPool: ConnectionPool,
        dispatcher: Dispatcher,
    ): OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http_cache"), 4L * 1024 * 1024))
        .connectionPool(connectionPool)
        .dispatcher(dispatcher)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()
}
