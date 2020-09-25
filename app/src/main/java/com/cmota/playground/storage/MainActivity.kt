package com.cmota.playground.storage

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.view.ActionMode
import androidx.core.app.ActivityCompat
import androidx.exifinterface.media.ExifInterface
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cmota.playground.storage.adapters.FilterAdapter
import com.cmota.playground.storage.adapters.ImagesAdapter
import com.cmota.playground.storage.adapters.ImagesItemDetailsLookup
import com.cmota.playground.storage.adapters.ImagesKeyProvider
import com.cmota.playground.storage.utils.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.StringBuilder

private const val GALLERY_COLUMNS = 5

private const val PERMISSION_STORAGE = 100
private const val PERMISSION_TRASH = 200
private const val PERMISSION_DELETE = 300
private const val PERMISSION_UPDATE = 400
private const val PERMISSION_FAVORITES = 500
private const val PERMISSION_SAVE_ON_LOCATION = 600

class MainActivity : AppCompatActivity(), ActionMode.Callback {

    private val viewModel: MainActivityViewModel by viewModels()

    private lateinit var filterAdapter: FilterAdapter
    private lateinit var imagesAdapter: ImagesAdapter
    private lateinit var tracker: SelectionTracker<String>

    private var actionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupFilters()
        setupGallery()
        setupObservers()

        if (!hasStoragePermission(baseContext)) {
            requestStoragePermission()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when(requestCode) {
            PERMISSION_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    viewModel.loadMedia(filterAdapter.selected)
                } else {
                    showPermissionMissing()
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            when(requestCode) {
                PERMISSION_DELETE -> {
                    if (hasSdkHigherThan(Build.VERSION_CODES.Q) && tracker.selection.size() > 1) {
                        //do nothing action already completed
                    } else {
                        deleteImages()
                    }
                }

                PERMISSION_UPDATE -> convertToBW()

                PERMISSION_SAVE_ON_LOCATION -> saveOnLocation(data?.data!!)
            }

            actionMode?.finish()
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun setupFilters() {
        filterAdapter =  FilterAdapter(
            FILTERS,
            clickAction = {
                when (it) {
                    R.string.filter_images, R.string.filter_videos -> {
                        viewModel.loadMedia(filterAdapter.selected)
                    }

                    R.string.filter_starred -> {
                        if (hasSdkHigherThan(Build.VERSION_CODES.Q)) {
                            viewModel.loadMediaFiltered(onlyFavorites = true, onlyTrash = false)
                        } else {
                            Snackbar.make(cl_container, getString(R.string.snack_not_available), Snackbar.LENGTH_SHORT).show()
                        }
                    }

                    R.string.filter_trash -> {
                        if (hasSdkHigherThan(Build.VERSION_CODES.Q)) {
                            viewModel.loadMediaFiltered(onlyFavorites = false, onlyTrash = true)
                        } else {
                            Snackbar.make(cl_container, getString(R.string.snack_not_available), Snackbar.LENGTH_SHORT).show()
                        }
                    }
                    else -> {
                        Snackbar.make(cl_container, getString(R.string.snack_not_available), Snackbar.LENGTH_SHORT).show()
                    }
                }
            })

        rv_filters.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(baseContext, LinearLayoutManager.HORIZONTAL, false)
            adapter = filterAdapter
        }
    }

    private fun setupGallery() {
        imagesAdapter = ImagesAdapter ()

        val spacing = resources.getDimensionPixelSize(R.dimen.grid) / 2
        rv_images.apply {
            setHasFixedSize(true)
            setPadding(spacing, spacing, spacing, spacing)
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(rect: Rect,
                                            view: View,
                                            parent: RecyclerView,
                                            state: RecyclerView.State) {
                    rect.set(spacing, spacing, spacing, spacing)
                }
            })
            layoutManager = GridLayoutManager(baseContext, GALLERY_COLUMNS)
            adapter = imagesAdapter
        }

        tracker = SelectionTracker.Builder(
            "imagesSelection",
            rv_images,
            ImagesKeyProvider(imagesAdapter),
            ImagesItemDetailsLookup(rv_images),
            StorageStrategy.createStringStorage()
        ).withSelectionPredicate(
            SelectionPredicates.createSelectAnything()
        ).build()

        tracker.addObserver(
            object : SelectionTracker.SelectionObserver<Long>() {
                override fun onSelectionChanged() {
                    super.onSelectionChanged()

                    if (actionMode == null) {
                        actionMode = startSupportActionMode(this@MainActivity)
                    }

                    val items = tracker.selection!!.size()
                    if (items > 0) {
                        actionMode?.title = getString(R.string.action_selected, items)
                    } else {
                        actionMode?.finish()
                    }
                }
            })

        imagesAdapter.tracker = tracker
    }

    private fun setupObservers() {
        viewModel.media.observe(this, { media ->
            imagesAdapter.submitList(media)

            if (media.isEmpty()) {
                Snackbar.make(cl_container, getString(R.string.snack_no_media), Snackbar.LENGTH_SHORT).show()
            }
        })

        if (hasStoragePermission(baseContext)) {
            viewModel.loadMedia(filterAdapter.selected)
        }

        viewModel.permissionNeededForDelete.observe(this, { sender ->
            if (sender == null) {
                actionMode?.finish()
            } else {
                startIntentSenderForResult(
                    sender,
                    PERMISSION_DELETE,
                    null,
                    0,
                    0,
                    0,
                    null)
            }
        })

        viewModel.permissionNeededForUpdate.observe(this, { sender ->
            if (sender == null) {
                actionMode?.finish()
            } else {
                startIntentSenderForResult(
                    sender,
                    PERMISSION_UPDATE,
                    null,
                    0,
                    0,
                    0,
                    null
                )
            }
        })
    }

    private fun requestStoragePermission() {
        val permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_STORAGE)
    }

    private fun showPermissionMissing() {
        Snackbar.make(cl_container, R.string.permission_declined, Snackbar.LENGTH_SHORT)
            .setAction(R.string.permission_action) {
                startActivity(getSettingsIntent(packageName))
            }.show()
    }

    //region ActionMode.Callback

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        when(item?.itemId) {
            R.id.action_favourites -> {
                if (hasSdkHigherThan(Build.VERSION_CODES.Q)) {
                    addToFavorites()
                } else {
                    Snackbar.make(cl_container, getString(R.string.snack_not_available), Snackbar.LENGTH_SHORT).show()
                }

                actionMode?.finish()
                return true
            }

            R.id.action_information -> {
                showInformation()
                return true
            }

            R.id.action_save_copy -> {
                duplicate()
                return true
            }

            R.id.action_save_copy_on_location -> {
                if (tracker.selection.size() > 1) {
                    Snackbar.make(cl_container, R.string.snack_only_one_selected, Snackbar.LENGTH_SHORT).show()
                } else {
                    openLocationPicker()
                }
                return true
            }

            R.id.action_black_white -> {
                convertToBW()
                return true
            }

            R.id.action_delete -> {
                deleteImages()
                return true
            }

            R.id.action_trash -> {
                if (hasSdkHigherThan(Build.VERSION_CODES.Q)) {
                    addToTrash()
                } else {
                    Snackbar.make(cl_container, getString(R.string.snack_not_available), Snackbar.LENGTH_SHORT).show()
                }

                actionMode?.finish()
                return true
            }

            else -> {
                return false
            }
        }
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        mode?.menuInflater?.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        tracker.clearSelection()
        actionMode = null
    }

    //endregion

    //verification already made on caller
    @SuppressLint("NewApi")
    private fun addToFavorites() {
        val media = imagesAdapter.currentList.filter {
            tracker.selection.contains("${it.id}")
        }

        val intent = viewModel.addToFavorites(media, true)
        startIntentSenderForResult(intent.intentSender, PERMISSION_FAVORITES, null, 0, 0, 0)
    }

    //verification already made on caller
    @SuppressLint("NewApi")
    private fun addToTrash() {
        val media = imagesAdapter.currentList.filter {
            tracker.selection.contains("${it.id}")
        }

        val intent = viewModel.addToTrash(media, true)
        startIntentSenderForResult(intent.intentSender, PERMISSION_TRASH, null, 0, 0, 0)
    }

    @SuppressLint("NewApi") //method only call from API 30 onwards
    private fun showInformation() {
        val media = imagesAdapter.currentList.filter {
            tracker.selection.contains("${it.id}")
        }

        val message: String

        if (media.size == 1) {
            message = getString(R.string.dialog_message_information, media[0].name, media[0].size, media[0].date)

            if (ActivityCompat.checkSelfPermission(baseContext,
                    Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val uri = MediaStore.setRequireOriginal(media[0].uri)
                contentResolver.openInputStream(uri).use { stream ->
                    ExifInterface(stream!!).run {
                        if (latLong != null) {
                            val coordinates = latLong!!.toList()
                            Log.d("TAG", "Coordinates = (${coordinates[0]}, ${coordinates[0]})")
                        }
                    }
                }
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_MEDIA_LOCATION), 10)
            }

        } else {
            val selected = StringBuilder()
            selected.append(getString(R.string.dialog_message_information_multiple))
            for (image in media) {
                selected.append("\n${image.name}")
            }

            message = selected.toString()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_information)
            .setMessage(message)
            .setCancelable(false)
            .setNegativeButton(R.string.dialog_action_negative) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                actionMode?.finish()
            }
            .show()
    }

    private fun duplicate() {
        val images = imagesAdapter.currentList.filter {
            tracker.selection.contains("${it.id}")
        }

        viewModel.duplicate(images)
        actionMode?.finish()
    }

    private fun openLocationPicker() {
        val media = imagesAdapter.currentList.first {
            tracker.selection.contains("${it.id}")
        }

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, media.name)
            type = "image/*"
        }

        startActivityForResult(intent, PERMISSION_SAVE_ON_LOCATION)
    }

    private fun saveOnLocation(uri: Uri) {
        val media = imagesAdapter.currentList.first {
            tracker.selection.contains("${it.id}")
        }

        viewModel.duplicate(media, uri)
    }

    private fun convertToBW() {
        val images = imagesAdapter.currentList.filter {
            tracker.selection.contains("${it.id}")
        }

        viewModel.convertToBW(images)
    }

    private fun deleteImages() {
        val images = imagesAdapter.currentList.filter {
            tracker.selection.contains("${it.id}")
        }

        viewModel.delete(images)
    }
}