package data.artwork.discogs

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// fetches discogs data
object DiscogsArtFetcher {
    private const val BASE_URL = "https://api.discogs.com/"

    // makes okhttp http request with api authorization (low-level)
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            val authenticatedRequest = originalRequest.newBuilder()
                .header("Authorization", "key=tYvsaskeJxOQbWoZSSkh, secret=vZuPZFFDerXIPrBfSNnNyDhXjpIUiyXi")
                .build()
            chain.proceed(authenticatedRequest)
        }
        .build()

    // makes retrofit http request (high-level)
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val discogsService: DiscogsAPI = retrofit.create(DiscogsAPI::class.java)
}