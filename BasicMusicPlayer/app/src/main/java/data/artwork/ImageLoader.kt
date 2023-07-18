package data.artwork

import android.widget.ImageView
import com.bumptech.glide.Glide

// class to load the image url
class ImageLoader {

    fun loadImage(imageView: ImageView, imageUrl: String) {
        Glide.with(imageView.context)
            .load(imageUrl)
            .into(imageView)
    }
}