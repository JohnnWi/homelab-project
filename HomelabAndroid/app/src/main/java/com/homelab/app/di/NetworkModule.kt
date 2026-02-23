package com.homelab.app.di

import com.homelab.app.data.remote.AuthInterceptor
import com.homelab.app.data.remote.SmartFallbackInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        smartFallbackInterceptor: SmartFallbackInterceptor,
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // TODO: Ridurre a BASIC in produzione
        }

        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS) // Breve per non bloccare in attesa
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(smartFallbackInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            // L'URL base è ininfluente perché lo SmartFallbackInterceptor rimpiazza l'host
            .baseUrl("https://placeholder.local/") 
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun providePortainerApi(retrofit: Retrofit): com.homelab.app.data.remote.api.PortainerApi {
        return retrofit.create(com.homelab.app.data.remote.api.PortainerApi::class.java)
    }

    @Provides
    @Singleton
    fun providePiholeApi(retrofit: Retrofit): com.homelab.app.data.remote.api.PiholeApi {
        return retrofit.create(com.homelab.app.data.remote.api.PiholeApi::class.java)
    }

    @Provides
    @Singleton
    fun provideBeszelApi(retrofit: Retrofit): com.homelab.app.data.remote.api.BeszelApi {
        return retrofit.create(com.homelab.app.data.remote.api.BeszelApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGiteaApi(retrofit: Retrofit): com.homelab.app.data.remote.api.GiteaApi {
        return retrofit.create(com.homelab.app.data.remote.api.GiteaApi::class.java)
    }
}
