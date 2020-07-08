package com.cmota.playground.storage.model

import android.net.Uri
import android.os.Parcelable
import androidx.recyclerview.widget.DiffUtil
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Media(val id: Long,
                 val path: String,
                 val uri: Uri,
                 val name: String,
                 val size: String,
                 val date: String): Parcelable {

    companion object {

        val DiffCallback = object : DiffUtil.ItemCallback<Media>() {
            override fun areItemsTheSame(oldItem: Media, newItem: Media) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Media, newItem: Media) =
                oldItem == newItem
        }
    }
}