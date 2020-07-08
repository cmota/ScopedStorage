package com.cmota.playground.storage.adapters

import androidx.recyclerview.selection.ItemKeyProvider

class ImagesKeyProvider(private val adapter: ImagesAdapter) : ItemKeyProvider<String>(SCOPE_CACHED) {

    override fun getKey(position: Int): String? =
        "${adapter.currentList[position].id}"

    override fun getPosition(key: String): Int =
        adapter.currentList.indexOfFirst {"${it.id}" == key}
}