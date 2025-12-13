package org.wxyc.wxycapp.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import data.WxycApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideWxycApi(): WxycApi {
        return Retrofit.Builder()
            .baseUrl("http://wxyc.info/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WxycApi::class.java)
    }
}
