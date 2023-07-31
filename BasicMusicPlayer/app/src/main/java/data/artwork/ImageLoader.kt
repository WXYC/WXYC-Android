package data.artwork

import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.basicmusicplayer.R

// class to load the image url
class ImageLoader {

    fun loadImage(imageView: ImageView, imageUrl: String) {

        val placeholderImage = AppCompatResources.getDrawable(imageView.context, R.drawable.wxyc_slash_logo)

       /* placeholderImage?.let {
            it.setTint(ContextCompat.getColor(imageView.context, android.R.color.transparent))
            it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight)
        }

        */

        Glide.with(imageView.context)
            .load(imageUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache the image
            .transition(DrawableTransitionOptions.withCrossFade()) // Transition effect
            .error(placeholderImage) // Placeholder image to display on error
            .into(imageView)
    }
}