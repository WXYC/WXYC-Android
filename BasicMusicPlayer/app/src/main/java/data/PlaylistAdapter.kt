package data

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.basicmusicplayer.R
import java.text.SimpleDateFormat
import java.util.*

//adapter class that binds the playlist data to the recyclerview
class PlaylistAdapter(private val playlistData: MutableList<Pair<String, String>>) :
    RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    //called by recyclerview when its time to create a new viewholder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        // context = the activity the recyclerview will be placed in
        val itemView = LayoutInflater.from(parent.context).inflate(
            R.layout.song_item,
            parent, false
        )
        return PlaylistViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val currentItem = playlistData[position]

        if (currentItem.first == "talkset") {
            //adjusts views
            holder.cardView.visibility = View.GONE
            holder.entryTypesTextView.visibility = View.VISIBLE
            holder.entryTypesTextView.text = currentItem.first

        } else if (currentItem.first == "breakpoint") {
            // adjusts views
            holder.cardView.visibility = View.GONE
            holder.entryTypesTextView.visibility = View.VISIBLE

            val convertedTime = convertTime(currentItem.second)

            holder.entryTypesTextView.text = convertedTime

        } else {
            //fills data
            holder.imageView.setImageResource(R.drawable.music_note)
            holder.songName.text = currentItem.first
            holder.artistName.text = currentItem.second
        }
    }

    override fun getItemCount() = playlistData.size


    // view holder represents recyclerview elements
    class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.image_view)
        val songName: TextView = itemView.findViewById(R.id.song_name)
        val artistName: TextView = itemView.findViewById(R.id.artist_name)
        val entryTypesTextView: TextView = itemView.findViewById(R.id.entryTypesTextView)
        val cardView: CardView = itemView.findViewById(R.id.cardView)

    }

    // function to convert breakpoint/talkset timestamp
    private fun convertTime(timestampString: String): String {
        val timestamp = timestampString.toLong()
        val date = Date(timestamp)
        val formatter = SimpleDateFormat("h a", Locale.getDefault())
        return formatter.format(date)
    }


}


