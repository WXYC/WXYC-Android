package org.wxyc.wxycapp.data.caching

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CachingModule {

    @Provides
    @Singleton
    @Named("MetadataCache")
    fun provideMetadataCache(@ApplicationContext context: Context): Cache {
        return DiskCache(context, "metadata_cache")
    }

    @Provides
    @Singleton
    @Named("MetadataCacheCoordinator")
    fun provideMetadataCacheCoordinator(@Named("MetadataCache") cache: Cache): CacheCoordinator {
        return CacheCoordinator(cache)
    }
    
    // Future: Add ArtworkCache if we move away from Glide/Coil's internal caching
    // or if we need specific manual caching for artwork assets.
}
