package com.homelab.app.di

import com.homelab.app.BuildConfig
import com.homelab.app.data.remote.AuthInterceptor
import com.homelab.app.data.remote.DebugLoggingInterceptor
import com.homelab.app.data.remote.HtmlDetectionInterceptor
import com.homelab.app.data.remote.SmartFallbackInterceptor
import com.homelab.app.data.remote.TlsRoutingCallFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(smartFallbackInterceptor)
            .addInterceptor(authInterceptor)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(debugLoggingInterceptor)
        }

        builder.addInterceptor(htmlDetectionInterceptor)

        return builder.build()
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

        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(smartFallbackInterceptor)
            .addInterceptor(authInterceptor)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(debugLoggingInterceptor)
        }

        builder.addInterceptor(htmlDetectionInterceptor)

        return builder
            .sslSocketFactory(sslSocketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(callFactory: TlsRoutingCallFactory, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://placeholder.local/")
            .callFactory(callFactory)
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
        retrofit: Retrofit
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

    @Provides
    @Singleton
    fun provideHealthchecksApi(retrofit: Retrofit): com.homelab.app.data.remote.api.HealthchecksApi {
        return retrofit.create(com.homelab.app.data.remote.api.HealthchecksApi::class.java)
    }

    @Provides
    @Singleton
    fun provideLinuxUpdateApi(retrofit: Retrofit): com.homelab.app.data.remote.api.LinuxUpdateApi {
        return retrofit.create(com.homelab.app.data.remote.api.LinuxUpdateApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDockhandApi(retrofit: Retrofit): com.homelab.app.data.remote.api.DockhandApi {
        return retrofit.create(com.homelab.app.data.remote.api.DockhandApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDockmonApi(retrofit: Retrofit): com.homelab.app.data.remote.api.DockmonApi {
        return retrofit.create(com.homelab.app.data.remote.api.DockmonApi::class.java)
    }

    @Provides
    @Singleton
    fun provideKomodoApi(retrofit: Retrofit): com.homelab.app.data.remote.api.KomodoApi {
        return retrofit.create(com.homelab.app.data.remote.api.KomodoApi::class.java)
    }

    @Provides
    @Singleton
    fun provideMaltrailApi(retrofit: Retrofit): com.homelab.app.data.remote.api.MaltrailApi {
        return retrofit.create(com.homelab.app.data.remote.api.MaltrailApi::class.java)
    }

    @Provides
    @Singleton
    fun provideUptimeKumaApi(retrofit: Retrofit): com.homelab.app.data.remote.api.UptimeKumaApi {
        return retrofit.create(com.homelab.app.data.remote.api.UptimeKumaApi::class.java)
    }

    @Provides
    @Singleton
    fun provideUnifiApi(retrofit: Retrofit): com.homelab.app.data.remote.api.UnifiApi {
        return retrofit.create(com.homelab.app.data.remote.api.UnifiApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCraftyApi(retrofit: Retrofit): com.homelab.app.data.remote.api.CraftyApi {
        return retrofit.create(com.homelab.app.data.remote.api.CraftyApi::class.java)
    }

    @Provides
    @Singleton
    fun provideTechnitiumApi(retrofit: Retrofit): com.homelab.app.data.remote.api.TechnitiumApi {
        return retrofit.create(com.homelab.app.data.remote.api.TechnitiumApi::class.java)
    }

    @Provides
    @Singleton
    fun providePatchmonApi(retrofit: Retrofit): com.homelab.app.data.remote.api.PatchmonApi {
        return retrofit.create(com.homelab.app.data.remote.api.PatchmonApi::class.java)
    }

    @Provides
    @Singleton
    fun providePangolinApi(retrofit: Retrofit): com.homelab.app.data.remote.api.PangolinApi {
        return retrofit.create(com.homelab.app.data.remote.api.PangolinApi::class.java)
    }

    @Provides
    @Singleton
    fun provideJellystatApi(retrofit: Retrofit): com.homelab.app.data.remote.api.JellystatApi {
        return retrofit.create(com.homelab.app.data.remote.api.JellystatApi::class.java)
    }

    @Provides
    @Singleton
    fun providePlexApi(retrofit: Retrofit): com.homelab.app.data.remote.api.PlexApi {
        return retrofit.create(com.homelab.app.data.remote.api.PlexApi::class.java)
    }

    @Provides
    @Singleton
    fun provideWakapiApi(retrofit: Retrofit): com.homelab.app.data.remote.api.WakapiApi {
        return retrofit.create(com.homelab.app.data.remote.api.WakapiApi::class.java)
    }

    @Provides
    @Singleton
    fun provideProxmoxApi(retrofit: Retrofit): com.homelab.app.data.remote.api.ProxmoxApi {
        return retrofit.create(com.homelab.app.data.remote.api.ProxmoxApi::class.java)
    }

    @Provides
    @Singleton
    fun providePterodactylApi(retrofit: Retrofit): com.homelab.app.data.remote.api.PterodactylApi {
        return retrofit.create(com.homelab.app.data.remote.api.PterodactylApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCalagopusApi(retrofit: Retrofit): com.homelab.app.data.remote.api.CalagopusApi {
        return retrofit.create(com.homelab.app.data.remote.api.CalagopusApi::class.java)
    }
}
