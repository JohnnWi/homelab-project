package com.homelab.app.di

import com.homelab.app.BuildConfig
import com.homelab.app.data.remote.AuthInterceptor
import com.homelab.app.data.remote.DebugLoggingInterceptor
import com.homelab.app.data.remote.HtmlDetectionInterceptor
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
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.inject.Named
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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
        authInterceptor: AuthInterceptor,
        debugLoggingInterceptor: DebugLoggingInterceptor,
        htmlDetectionInterceptor: HtmlDetectionInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
        }

        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        val sslSocketFactory = sslContext.socketFactory
        val trustManager = trustAllCerts.first() as X509TrustManager

        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(smartFallbackInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(debugLoggingInterceptor)
            .addInterceptor(htmlDetectionInterceptor)
            .addInterceptor(loggingInterceptor)
            .sslSocketFactory(sslSocketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    @Provides
    @Singleton
    @Named("insecure")
    fun provideInsecureOkHttpClient(
        smartFallbackInterceptor: SmartFallbackInterceptor,
        authInterceptor: AuthInterceptor,
        debugLoggingInterceptor: DebugLoggingInterceptor,
        htmlDetectionInterceptor: HtmlDetectionInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
        }

        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        val sslSocketFactory = sslContext.socketFactory
        val trustManager = trustAllCerts.first() as X509TrustManager

        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(smartFallbackInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(debugLoggingInterceptor)
            .addInterceptor(htmlDetectionInterceptor)
            .addInterceptor(loggingInterceptor)
            .sslSocketFactory(sslSocketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://placeholder.local/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    @Named("insecure")
    fun provideInsecureRetrofit(
        @Named("insecure") okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://placeholder.local/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun providePortainerApi(
        @Named("insecure") retrofit: Retrofit
    ): com.homelab.app.data.remote.api.PortainerApi {
        return retrofit.create(com.homelab.app.data.remote.api.PortainerApi::class.java)
    }

    @Provides
    @Singleton
    fun providePiholeApi(retrofit: Retrofit): com.homelab.app.data.remote.api.PiholeApi {
        return retrofit.create(com.homelab.app.data.remote.api.PiholeApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAdGuardHomeApi(retrofit: Retrofit): com.homelab.app.data.remote.api.AdGuardHomeApi {
        return retrofit.create(com.homelab.app.data.remote.api.AdGuardHomeApi::class.java)
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

    @Provides
    @Singleton
    fun provideNginxProxyManagerApi(retrofit: Retrofit): com.homelab.app.data.remote.api.NginxProxyManagerApi {
        return retrofit.create(com.homelab.app.data.remote.api.NginxProxyManagerApi::class.java)
    }
}
