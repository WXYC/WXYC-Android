package org.wxyc.wxycapp.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import data.artwork.discogs.DiscogsAPI
import data.artwork.itunes.ITunesAPI
import data.artwork.lastfm.LastFmAPI
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @Named("Discogs")
    fun provideDiscogsOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val authenticatedRequest = originalRequest.newBuilder()
                    .header("Authorization", "key=tYvsaskeJxOQbWoZSSkh, secret=vZuPZFFDerXIPrBfSNnNyDhXjpIUiyXi")
                    .build()
                chain.proceed(authenticatedRequest)
            }
            .build()
    }

    @Provides
    @Singleton
    @Named("Default")
    fun provideDefaultOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    @Provides
    @Singleton
    fun provideDiscogsAPI(@Named("Discogs") okHttpClient: OkHttpClient): DiscogsAPI {
        return Retrofit.Builder()
            .baseUrl("https://api.discogs.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DiscogsAPI::class.java)
    }

    @Provides
    @Singleton
    fun provideITunesAPI(@Named("Default") okHttpClient: OkHttpClient): ITunesAPI {
        return Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ITunesAPI::class.java)
    }

    @Provides
    @Singleton
    fun provideLastFmAPI(@Named("Default") okHttpClient: OkHttpClient): LastFmAPI {
        return Retrofit.Builder()
            .baseUrl("http://ws.audioscrobbler.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LastFmAPI::class.java)
    }
    
    // Metadata API Services
    
    @Provides
    @Singleton
    fun provideDiscogsApiService(@Named("Default") okHttpClient: OkHttpClient): org.wxyc.wxycapp.data.api.DiscogsApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.discogs.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(org.wxyc.wxycapp.data.api.DiscogsApiService::class.java)
    }
    
    @Provides
    @Singleton
    fun provideSpotifyApiService(@Named("Default") okHttpClient: OkHttpClient): org.wxyc.wxycapp.data.api.SpotifyApiService {
        return Retrofit.Builder()
            .baseUrl("https://accounts.spotify.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(org.wxyc.wxycapp.data.api.SpotifyApiService::class.java)
    }
    
    @Provides
    @Singleton
    @Named("SpotifySearch")
    fun provideSpotifySearchService(@Named("Default") okHttpClient: OkHttpClient): org.wxyc.wxycapp.data.api.SpotifyApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.spotify.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(org.wxyc.wxycapp.data.api.SpotifyApiService::class.java)
    }
    
    @Provides
    @Singleton
    fun provideITunesApiService(@Named("Default") okHttpClient: OkHttpClient): org.wxyc.wxycapp.data.api.ITunesApiService {
        return Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(org.wxyc.wxycapp.data.api.ITunesApiService::class.java)
    }
}
