package com.absolute.drvcam

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var root: LinearLayout
    private lateinit var preview: ImageView
    private lateinit var videoPreview: VideoView
    private lateinit var status: TextView
    private lateinit var createButton: Button
    private lateinit var targetButton: Button

    private val executor = Executors.newSingleThreadExecutor()

    private var selectedUri: Uri? = null
    private var selectedTargetPackage: String? = null
    private var rotation = 0f
    private var mirrored = false
    private var looping = false
    private var isCreating = false

    private val photoPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Some document providers do not support persistable permissions.
                }
                selectedUri = uri
                getSharedPreferences("absolute_drvcam", MODE_PRIVATE)
                    .edit().putString("selected_photo_uri", uri.toString()).apply()
                videoPreview.stopPlayback()
                videoPreview.visibility = View.GONE
                preview.visibility = View.VISIBLE

                try {
                    preview.setImageURI(uri)
                    applyImageTransform()
                    createVideoAutomatically(uri)
                } catch (e: Exception) {
                    status.text = "Could not load photo."
                    toast("Photo could not be opened.")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.rgb(12, 19, 25))
        }

        val title = TextView(this).apply {
            text = "ABSOLUTE DRVCAM"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(70, 220, 195))
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "PHOTO MEDIA STUDIO"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
        }
        root.addView(subtitle)

        val previewContainer = FrameLayoutCompat.create(this)
        preview = ImageView(this).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(28, 39, 46))
        }

        videoPreview = VideoView(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.rgb(28, 39, 46))
            setOnCompletionListener {
                if (looping) {
                    seekTo(0)
                    start()
                }
            }
        }

        previewContainer.addView(
            preview,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        previewContainer.addView(
            videoPreview,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(
            previewContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply {
                setMargins(0, 16, 0, 12)
            }
        )

        addButton("SELECT PHOTO") {
            photoPicker.launch(arrayOf("image/*"))
        }

        addButton("ROTATE") {
            rotation = (rotation + 90f) % 360f
            applyImageTransform()
        }

        addButton("MIRROR") {
            mirrored = !mirrored
            applyImageTransform()
        }

        addButton("LOOP: OFF") { button ->
            looping = !looping
            button.text = if (looping) "LOOP: ON" else "LOOP: OFF"
            if (videoPreview.visibility == View.VISIBLE && looping &&
                !videoPreview.isPlaying
            ) {
                videoPreview.start()
            }
        }

        createButton = addButton("CREATE 30-SECOND MP4") {
            val uri = selectedUri
            if (uri != null) createVideoAutomatically(uri)
            else toast("Select a photo first.")
        }

        targetButton = addButton("SELECT TARGET APP") {
            showTargetApps()
        }

        status = TextView(this).apply {
            text = "Choose a photo. A 30-second video will be created automatically."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(0, 10, 0, 8)
        }
        root.addView(status)

        setContentView(root)

        // Restore the last selected photo after app restart.
        val savedPhoto = getSharedPreferences("absolute_drvcam", MODE_PRIVATE)
            .getString("selected_photo_uri", null)
        if (savedPhoto != null) {
            try {
                selectedUri = Uri.parse(savedPhoto)
                preview.setImageURI(selectedUri)
                preview.visibility = View.VISIBLE
                applyImageTransform()
                status.text = "Photo restored. MP4 files are saved separately."
            } catch (_: Exception) {
                selectedUri = null
                getSharedPreferences("absolute_drvcam", MODE_PRIVATE)
                    .edit().remove("selected_photo_uri").apply()
            }
        }
    }

    private fun addButton(
        label: String,
        action: (Button) -> Unit
    ): Button {
        val button = Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(0, 125, 112))
        }
        button.setOnClickListener { action(button) }
        root.addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 3, 0, 3)
            }
        )
        return button
    }

    private fun applyImageTransform() {
        preview.rotation = rotation
        preview.scaleX = if (mirrored) -1f else 1f
    }

    private fun createVideoAutomatically(uri: Uri) {
        if (isCreating) {
            toast("Video is already being created.")
            return
        }

        isCreating = true
        createButton.isEnabled = false
        status.text = "Creating 30-second MP4 automatically..."

        executor.execute {
            var bitmap: Bitmap? = null
            var tempFile: File? = null

            try {
                bitmap = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: error("Unable to read selected photo.")

                tempFile = File(cacheDir, "absolute_${System.currentTimeMillis()}.mp4")
                encodePhoto(bitmap, tempFile!!, rotation, mirrored)

                val savedUri = saveVideo(tempFile!!)
                runOnUiThread {
                    // Keep the selected photo visible; creating an MP4 must not clear it.
                    videoPreview.stopPlayback()
                    videoPreview.visibility = View.GONE
                    preview.visibility = View.VISIBLE
                    preview.setImageURI(selectedUri ?: uri)
                    applyImageTransform()
                    status.text = "MP4 saved in Movies/ABSOLUTE DRVCAM. Selected photo retained."
                    toast("30-second MP4 saved. Photo is still selected.")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Video failed: ${e.message ?: "Unknown error"}"
                    toast("Video could not be created.")
                }
            } finally {
                bitmap?.recycle()
                tempFile?.delete()
                runOnUiThread {
                    isCreating = false
                    createButton.isEnabled = true
                }
            }
        }
    }

    private fun encodePhoto(
        bitmap: Bitmap,
        output: File,
        rotationDegrees: Float,
        mirror: Boolean
    ) {
        val width = 720
        val height = 1280
        val fps = 15
        val frames = fps * 30

        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC, width, height
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfoSurface.COLOR_FORMAT
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var inputSurface: Surface? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()

            muxer = MediaMuxer(
                output.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            val info = MediaCodec.BufferInfo()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val matrix = Matrix()
            val scale = minOf(
                width.toFloat() / bitmap.width,
                height.toFloat() / bitmap.height
            )
            matrix.setScale(scale, scale)
            matrix.postTranslate(
                (width - bitmap.width * scale) / 2f,
                (height - bitmap.height * scale) / 2f
            )
            matrix.postRotate(rotationDegrees, width / 2f, height / 2f)
            if (mirror) matrix.postScale(-1f, 1f, width / 2f, height / 2f)

            repeat(frames) {
                val canvas: Canvas = inputSurface.lockCanvas(null)
                try {
                    canvas.drawColor(Color.BLACK)
                    canvas.drawBitmap(bitmap, matrix, paint)
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                while (true) {
                    val result = encoder.dequeueOutputBuffer(info, 0)
                    when {
                        result == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                        result == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!muxerStarted) { "Unexpected encoder format change." }
                            trackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        result >= 0 -> {
                            val buffer = encoder.getOutputBuffer(result)
                            if (buffer != null && info.size > 0 && muxerStarted) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                muxer.writeSampleData(trackIndex, buffer, info)
                            }
                            encoder.releaseOutputBuffer(result, false)
                        }
                    }
                }
            }

            encoder.signalEndOfInputStream()
            var finished = false
            while (!finished) {
                when (val result = encoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    else -> if (result >= 0) {
                        val buffer = encoder.getOutputBuffer(result)
                        if (buffer != null && info.size > 0 && muxerStarted) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, buffer, info)
                        }
                        finished =
                            (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        encoder.releaseOutputBuffer(result, false)
                    }
                }
            }
            check(muxerStarted) { "Encoder produced no video track." }
        } finally {
            try { inputSurface?.release() } catch (_: Exception) {}
            try { encoder.stop() } catch (_: Exception) {}
            encoder.release()
            try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
            muxer?.release()
        }
    }

    private fun saveVideo(file: File): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/ABSOLUTE DRVCAM"
            )
        }

        val uri = contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
        ) ?: error("Could not save video.")

        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Could not write video.")
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }

        return uri
    }

    private fun showTargetApps() {
        // Lists installed apps visible to Android's package-visibility rules.
        // For a complete list on Android 11+, add QUERY_ALL_PACKAGES to the manifest.
        val apps = try {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != packageName }
                .map { appInfo ->
                    appInfo to packageManager.getApplicationLabel(appInfo).toString()
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.second })
        } catch (e: Exception) {
            status.text = "Could not load apps. Check QUERY_ALL_PACKAGES in AndroidManifest.xml."
            toast("Could not load installed apps. Check the manifest permission.")
            return
        }

        if (apps.isEmpty()) {
            toast("No apps found. Check app visibility in AndroidManifest.xml.")
            return
        }

        val labels = apps.map { (appInfo, label) ->
            // Include package name so apps with identical names can be distinguished.
            "$label\n${appInfo.packageName}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Target App (${apps.size})")
            .setItems(labels) { _, index ->
                val appInfo = apps[index].first
                val label = apps[index].second
                selectedTargetPackage = appInfo.packageName
                targetButton.text = "TARGET: $label"
                status.text =
                    "Selected target: $label. Selecting an app does not replace its camera source."
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private object MediaCodecInfoSurface {
        const val COLOR_FORMAT = 0x7F000789
    }

    private object FrameLayoutCompat {
        fun create(context: android.content.Context): android.widget.FrameLayout {
            return android.widget.FrameLayout(context)
        }
    }
}
