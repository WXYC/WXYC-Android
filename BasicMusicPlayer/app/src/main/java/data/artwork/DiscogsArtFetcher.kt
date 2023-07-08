package data.artwork

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// fetches discogs data
object DiscogsArtFetcher {
    private const val BASE_URL = "https://api.discogs.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            val authenticatedRequest = originalRequest.newBuilder()
                .build()
            chain.proceed(authenticatedRequest)
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val discogsService: DiscogsAPI = retrofit.create(DiscogsAPI::class.java)
}