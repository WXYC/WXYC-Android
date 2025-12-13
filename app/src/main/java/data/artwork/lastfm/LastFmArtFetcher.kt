package data.artwork.lastfm

import data.artwork.itunes.ITunesAPI
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object LastFmArtFetcher {
    private const val BASE_URL = "http://ws.audioscrobbler.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val lastFmService: LastFmAPI = retrofit.create(LastFmAPI::class.java)
}