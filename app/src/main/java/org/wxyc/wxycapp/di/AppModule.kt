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
import org.wxyc.wxycapp.BuildConfig
import org.wxyc.wxycapp.requestline.DeviceFingerprintStore
import org.wxyc.wxycapp.requestline.RequestLineClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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
        return RequestLineClient(
            httpClient = OkHttpClient(),
            endpoint = RequestLineClient.REQUEST_O_MATIC_URL,
            userAgent = "WXYC-Android/${BuildConfig.VERSION_NAME}",
            fingerprint = fingerprints::get,
        )
    }
}
