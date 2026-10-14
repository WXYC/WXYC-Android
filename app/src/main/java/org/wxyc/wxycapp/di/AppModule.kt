package org.wxyc.wxycapp.di

import android.content.Context
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import data.WxycApi
import okhttp3.OkHttpClient
import org.wxyc.wxycapp.analytics.PostHogManager
import org.wxyc.wxycapp.BuildConfig
import org.wxyc.wxycapp.auth.AnonymousAuthenticator
import org.wxyc.wxycapp.auth.AuthInterceptor
import org.wxyc.wxycapp.auth.AuthWireClient
import org.wxyc.wxycapp.auth.EncryptedSessionStore
import org.wxyc.wxycapp.auth.KeystoreSessionCipher
import org.wxyc.wxycapp.requestline.DeviceFingerprintStore
import org.wxyc.wxycapp.requestline.RequestLineClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Marks the [OkHttpClient] that sends the anonymous session's bearer JWT. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Authenticated

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideWxycApi(): WxycApi {
        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        return Retrofit.Builder()
            .baseUrl("$API_ORIGIN/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(WxycApi::class.java)
    }

    /** One per process, so the request line and auth never mint two fingerprints at once. */
    @Provides
    @Singleton
    fun provideDeviceFingerprintStore(@ApplicationContext context: Context): DeviceFingerprintStore =
        DeviceFingerprintStore(context.getSharedPreferences(DeviceFingerprintStore.PREFS_NAME, Context.MODE_PRIVATE))

    @Provides
    @Singleton
    fun provideAnonymousAuthenticator(
        @ApplicationContext context: Context,
        fingerprints: DeviceFingerprintStore,
    ): AnonymousAuthenticator = AnonymousAuthenticator(
        wire = AuthWireClient(
            httpClient = OkHttpClient(),
            baseUrl = API_ORIGIN,
            userAgent = USER_AGENT,
            fingerprint = fingerprints::get,
        ),
        store = EncryptedSessionStore(
            context.getSharedPreferences(EncryptedSessionStore.PREFS_NAME, Context.MODE_PRIVATE),
            KeystoreSessionCipher(),
        ),
    )

    /** For JWT-gated Backend-Service routes (the `/proxy` family, `/concerts`); call it off the main thread. */
    @Provides
    @Singleton
    @Authenticated
    fun provideAuthenticatedHttpClient(authenticator: AnonymousAuthenticator): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(authenticator))
            .build()

    @Provides
    @Singleton
    fun provideRequestLineClient(fingerprints: DeviceFingerprintStore): RequestLineClient {
        // Generous next to OkHttp's 10 s default, because request-o-matic is
        // slow by design on a cache miss: its own benchmarks put an uncached
        // /request at 6-30 s (its downstream lookup client alone allows 20 s
        // per attempt), against 273-551 ms cached. A 10 s read timeout would
        // report "Network error" for requests that are about to succeed.
        val httpClient = OkHttpClient.Builder()
            .callTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
        return RequestLineClient(
            httpClient = httpClient,
            endpoint = BuildConfig.REQUEST_O_MATIC_URL,
            userAgent = USER_AGENT,
            fingerprint = fingerprints::get,
            capture = PostHogManager::capture,
        )
    }

    private const val API_ORIGIN = "https://api.wxyc.org"
    private val USER_AGENT = "WXYC-Android/${BuildConfig.VERSION_NAME}"
}
