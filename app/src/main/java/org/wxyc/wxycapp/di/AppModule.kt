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
import org.wxyc.wxycapp.requestline.DeviceFingerprintStore
import org.wxyc.wxycapp.requestline.RequestLineClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

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
            .baseUrl("https://api.wxyc.org/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(WxycApi::class.java)
    }

    @Provides
    @Singleton
    fun provideRequestLineClient(@ApplicationContext context: Context): RequestLineClient {
        val fingerprints = DeviceFingerprintStore(
            context.getSharedPreferences(DeviceFingerprintStore.PREFS_NAME, Context.MODE_PRIVATE)
        )
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
            userAgent = "WXYC-Android/${BuildConfig.VERSION_NAME}",
            fingerprint = fingerprints::get,
            capture = PostHogManager::capture,
        )
    }
}
