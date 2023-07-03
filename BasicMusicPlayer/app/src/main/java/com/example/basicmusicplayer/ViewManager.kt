package com.example.basicmusicplayer

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import data.PlaylistAdapter
import data.PlaylistDetails

class ViewManager {

    fun setupRecyclerView(recyclerView: RecyclerView, playlistDetailsList: MutableList<PlaylistDetails>) {
        recyclerView.adapter = PlaylistAdapter(playlistDetailsList)
        recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
        recyclerView.setHasFixedSize(true)
    }
}