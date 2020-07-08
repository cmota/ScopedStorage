package com.cmota.playground.storage

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.*
import android.database.ContentObserver
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.cmota.playground.storage.model.Media
import com.cmota.playground.storage.utils.hasSdkHigherThan
import com.cmota.playground.storage.utils.registerObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Class adapted from Android Storage samples repository, available at:
 * - https://github.com/android/storage-samples
 */
private const val TAG = "MainActivityViewModel"

class MainActivityViewModel(application: Application): AndroidViewModel(application) {

    private val _media = MutableLiveData<List<Media>>()
    val media: LiveData<List<Media>> get() = _media

    private val _permissionNeededForDelete = MutableLiveData<IntentSender?>()
    val permissionNeededForDelete: LiveData<IntentSender?> = _permissionNeededForDelete

    private val _permissionNeededForUpdate = MutableLiveData<IntentSender?>()
    val permissionNeededForUpdate: LiveData<IntentSender?> = _permissionNeededForUpdate

    private var contentObserver: ContentObserver? = null

    //region Query

    fun loadMedia(media: Int) {
        viewModelScope.launch {
            val imageList = queryMedia(media)
            _media.postValue(imageList)

            if (contentObserver == null) {
                contentObserver = getApplication<Application>().contentResolver.registerObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI) {
                    loadMedia(media)
                }
            }
        }
    }

    @SuppressLint("NewApi") //method only call from API 30 onwards
    fun loadMediaFiltered(onlyFavorites: Boolean, onlyTrash: Boolean) {
        viewModelScope.launch {
            if (onlyFavorites) {
                val selection = "${MediaStore.Images.Media.IS_FAVORITE} = 1"
                val imageList = queryImages(selection)
                _media.postValue(imageList)
            } else if (onlyTrash) {
                val imageList = queryTrash()
                _media.postValue(imageList)
            }
        }
    }

    private suspend fun queryMedia(media: Int): List<Media> {
        return if (media == R.string.filter_images) {
            queryImages(null)
        } else {
            queryVideos()
        }
    }

    @SuppressLint("NewApi") //method only call from API 30 onwards
    private suspend fun queryTrash(): List<Media> {
        val images = mutableListOf<Media>()

        withContext(Dispatchers.IO) {

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.IS_TRASHED)

            val bundle = Bundle()
            bundle.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, 1)

            getApplication<Application>().contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                bundle,
                null
            )?.use { cursor ->

                while (cursor.moveToNext()) {

                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    val size = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.SIZE)) ?: ""
                    val date = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)) ?: ""
                    val trash = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.IS_TRASHED))

                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    if (trash == 1) {
                        images += Media(id, "${uri.path}", uri, name, size, date)
                    }
                }

                Log.v(TAG, "Found ${images.size} images")
            }
        }

        return images
    }

    private suspend fun queryImages(selection: String?): List<Media> {
        val images = mutableListOf<Media>()

        withContext(Dispatchers.IO) {

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_MODIFIED)

            val selectionArgs = null

            val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

            getApplication<Application>().contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->

                while (cursor.moveToNext()) {

                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    val size = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.SIZE)) ?: ""
                    val date = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)) ?: ""

                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    images += Media(id, "${uri.path}", uri, name, size, date)
                }

                Log.v(TAG, "Found ${images.size} images")
            }
        }

        return images
    }

    private suspend fun queryVideos(): List<Media> {
        val videos = mutableListOf<Media>()

        withContext(Dispatchers.IO) {

            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_MODIFIED)

            val selection = null
            val selectionArgs = null

            val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

            getApplication<Application>().contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->

                while (cursor.moveToNext()) {

                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME))
                    val size = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.SIZE)) ?: ""
                    val date = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)) ?: ""

                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    videos += Media(id, "${uri.path}", uri, name, size, date)
                }

                Log.v(TAG, "Found ${videos.size} videos")
            }
        }

        return videos
    }

    //endregion

    //region New media

    fun duplicate(items: List<Media>) {
        viewModelScope.launch {
            for (media in items) {
                duplicateMedia(getApplication<Application>().contentResolver, media)
            }
        }
    }

    private suspend fun duplicateMedia(resolver: ContentResolver, media: Media) {
        withContext(Dispatchers.IO) {
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            val date = System.currentTimeMillis()
            val newMedia = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "${media.name}-cp")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.DATE_ADDED, date)
                put(MediaStore.MediaColumns.DATE_MODIFIED, date)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val newMediaUri = resolver.insert(collection, newMedia)

            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, media.uri))
            } else {
                MediaStore.Images.Media.getBitmap(resolver, media.uri)
            }

            resolver.openOutputStream(newMediaUri!!, "w").use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                it?.close()
            }

            newMedia.clear()
            newMedia.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(newMediaUri, newMedia, null, null)
        }
    }

    fun duplicate(media: Media, uri: Uri) {
        viewModelScope.launch {
            duplicateMedia(getApplication<Application>().contentResolver, media, uri)
        }
    }

    private suspend fun duplicateMedia(resolver: ContentResolver, media: Media, uri: Uri) {
        withContext(Dispatchers.IO) {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, media.uri))
            } else {
                MediaStore.Images.Media.getBitmap(resolver, media.uri)
            }

            resolver.openOutputStream(uri, "w").use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            }
        }
    }


    //endregion

    //region Update media

    fun convertToBW(images: List<Media>) {
        viewModelScope.launch {
            val paint = Paint()
            try {
                for (image in images) {
                    saveImageBW(getApplication<Application>().contentResolver, image, paint)
                }
            } catch (securityException: SecurityException) {
                if (hasSdkHigherThan(Build.VERSION_CODES.Q)) {
                    val recoverableSecurityException =
                        securityException as? RecoverableSecurityException ?: throw securityException

                    _permissionNeededForUpdate.postValue(
                        recoverableSecurityException.userAction.actionIntent.intentSender)
                } else
                    throw securityException
            }
        }
    }

    private suspend fun saveImageBW(resolver: ContentResolver, media: Media, paint: Paint) {
        withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options()
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val bitmap = MediaStore.Images.Media.getBitmap(resolver, media.uri)

            val bitmapBW = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmapBW)

            val matrix = ColorMatrix()
            matrix.setSaturation(0f)
            val colorMatrix = ColorMatrixColorFilter(matrix)
            paint.colorFilter = colorMatrix

            canvas.drawBitmap(bitmap, 0.0f, 0.0f, paint)

            resolver.openOutputStream(media.uri, "w").use {
                bitmapBW.compress(Bitmap.CompressFormat.JPEG, 100, it)
                it?.close()
            }
        }
    }

    //endregion

    //region Delete

    fun delete(items: List<Media>) {
        viewModelScope.launch {
            if (hasSdkHigherThan(Build.VERSION_CODES.Q) && items.size > 1) {
                deleteMediaBulk(getApplication<Application>().contentResolver, items)
            } else {
                for (media in items) {
                    deleteMedia(getApplication<Application>().contentResolver, media)
                }
            }
        }
    }

    private suspend fun deleteMedia(resolver: ContentResolver, item: Media) {
        withContext(Dispatchers.IO) {
            try {
                resolver.delete(
                    item.uri, "${MediaStore.Images.Media._ID} = ?",
                    arrayOf(item.id.toString())
                )

                _permissionNeededForDelete.postValue(null)

            } catch (securityException: SecurityException) {
                if (hasSdkHigherThan(Build.VERSION_CODES.Q)) {
                    val recoverableSecurityException =
                        securityException as? RecoverableSecurityException ?: throw securityException

                    _permissionNeededForDelete.postValue(
                        recoverableSecurityException.userAction.actionIntent.intentSender)
                } else
                    throw securityException
            }
        }
    }

    @SuppressLint("NewApi") //method only call from API 30 onwards
    private fun deleteMediaBulk(resolver: ContentResolver, items: List<Media>) {
        val uris = items.map { it.uri }
        _permissionNeededForDelete.postValue(
            MediaStore.createDeleteRequest(resolver, uris).intentSender)
    }

    //endregion

    //region Favorites

    @SuppressLint("NewApi") //method only call from API 30 onwards
    fun addToFavorites(items: List<Media>, state: Boolean): PendingIntent {
        val resolver = getApplication<Application>().contentResolver
        val uris = items.map { it.uri }
        return MediaStore.createFavoriteRequest(resolver, uris, state)
    }

    //endregion

    //region Trash

    @SuppressLint("NewApi") //method only call from API 30 onwards
    fun addToTrash(items: List<Media>, state: Boolean): PendingIntent {
        val resolver = getApplication<Application>().contentResolver
        val uris = items.map { it.uri }
        return MediaStore.createTrashRequest(resolver, uris, state)
    }

    //endregion
}