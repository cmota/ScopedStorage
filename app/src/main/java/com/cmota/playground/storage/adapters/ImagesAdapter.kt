package com.cmota.playground.storage.adapters

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.cmota.playground.storage.R
import com.cmota.playground.storage.adapters.ImagesAdapter.*
import com.cmota.playground.storage.model.Media
import com.cmota.playground.storage.model.Media.Companion.DiffCallback
import kotlinx.android.synthetic.main.item_image.view.*

class ImagesAdapter: ListAdapter<Media, ImageViewHolder>(DiffCallback) {

    var tracker: SelectionTracker<String>? = null

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ImageViewHolder(inflater.inflate(R.layout.item_image, parent, false))
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val image = getItem(position)

        tracker?.let {
            Glide.with(holder.image)
                .load(image.uri)
                .signature(ObjectKey("${image.id}.${image.date}"))
                .centerCrop()
                .into(holder.image)

            if(it.isSelected("${image.id}")) {
                holder.image.setColorFilter(
                    ContextCompat.getColor(holder.image.context,
                    R.color.color60PrimaryDark),
                    PorterDuff.Mode.SRC_OVER)
            } else {
                holder.image.clearColorFilter()
            }
        }
    }

    override fun getItemId(position: Int): Long {
        return currentList[position].id
    }

    inner class ImageViewHolder(imageView: View): RecyclerView.ViewHolder(imageView) {
        val image: ImageView = imageView.iv_image

        fun getItemDetails(): ItemDetailsLookup.ItemDetails<String> =

            object : ItemDetailsLookup.ItemDetails<String>() {
                override fun getPosition(): Int = adapterPosition

                override fun getSelectionKey(): String? = "${getItem(adapterPosition).id}"
            }
    }
}