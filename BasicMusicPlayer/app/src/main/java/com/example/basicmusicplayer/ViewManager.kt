package com.example.basicmusicplayer

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import data.PlaylistAdapter
import data.PlaylistDetails

// Class to manage the views in the PlayerActivity
class ViewManager {

    fun setupRecyclerView(recyclerView: RecyclerView, playlistDetailsList: MutableList<PlaylistDetails>) {
        recyclerView.adapter = PlaylistAdapter(playlistDetailsList)
        recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
        recyclerView.setHasFixedSize(true)
    }
}