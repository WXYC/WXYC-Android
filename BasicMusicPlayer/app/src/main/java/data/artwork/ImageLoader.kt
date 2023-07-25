package data.artwork

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.basicmusicplayer.R

// class to load the image url
class ImageLoader {

    fun loadImage(imageView: ImageView, imageUrl: String) {
        Glide.with(imageView.context)
            .load(imageUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache the image
            .transition(DrawableTransitionOptions.withCrossFade()) // Transition effect
            .error(R.drawable.wxyc_slash_logo) // Placeholder image to display on error
            .into(imageView)
    }
}