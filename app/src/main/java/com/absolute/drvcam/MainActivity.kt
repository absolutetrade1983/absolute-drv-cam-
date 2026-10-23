package com.absolute.drvcam

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
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
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var preview: ImageView
    private lateinit var status: TextView

    private var selectedUri: Uri? = null
    private var rotation = 0f
    private var mirrored = false
    private var looping = false
    private val executor = Executors.newSingleThreadExecutor()

    private val picker =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            uri: Uri? ->
            if (uri != null) {
                selectedUri = uri
                preview.setImageURI(uri)
                preview.visibility = View.VISIBLE
                status.text = "Photo selected. Ready to create video."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 16)
            gravity = Gravity.CENTER_HORIZONTAL
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

        preview = ImageView(this).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(28, 39, 46))
        }

        root.addView(
            preview,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply {
                setMargins(0, 18, 0, 14)
            }
        )

        addButton(root, "SELECT PHOTO") {
            picker.launch("image/*")
        }

        addButton(root, "ROTATE") {
            rotation = (rotation + 90f) % 360f
            preview.rotation = rotation
        }

        addButton(root, "MIRROR") {
            mirrored = !mirrored
            preview.scaleX = if (mirrored) -1f else 1f
        }

        addButton(root, "LOOP: OFF") { button ->
            looping = !looping
            button.text = if (looping) "LOOP: ON" else "LOOP: OFF"
        }

        addButton(root, "CREATE 30-SECOND MP4") {
            val uri = selectedUri
            if (uri == null) {
                toast("Please select a photo first.")
            } else {
                createButtonEnabled(root, false)
                status.text = "Creating video. Please wait..."
                executor.execute {
                    try {
                        val bitmap = contentResolver.openInputStream(uri)
                            ?.use { BitmapFactory.decodeStream(it) }
                            ?: error("Could not read the selected photo.")

                        val output = File(cacheDir, "absolute_${System.currentTimeMillis()}.mp4")
                        try {
                            encodePhoto(bitmap, output)
                        } finally {
                            bitmap.recycle()
                        }

                        val savedUri = saveVideo(output)
                        output.delete()

                        runOnUiThread {
                            status.text = "Video saved to Movies/ABSOLUTE DRVCAM"
                            toast("30-second MP4 saved successfully.")
                            createButtonEnabled(root, true)
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            status.text = "Video creation failed: ${e.message ?: "Unknown error"}"
                            toast("Could not create video.")
                            createButtonEnabled(root, true)
                        }
                    }
                }
            }
        }

        status = TextView(this).apply {
            text = "Choose a photo to begin."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(0, 12, 0, 8)
        }
        root.addView(status)

        setContentView(root)
    }

    private fun encodePhoto(bitmap: Bitmap, file: File) {
        val width = 720
        val height = 1280
        val fps = 15
        val durationSeconds = 30
        val frameCount = fps * durationSeconds

        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC, width, height
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfoCompat.COLOR_FORMAT_SURFACE
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var surface: Surface? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = encoder.createInputSurface()
            encoder.start()
            muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val info = MediaCodec.BufferInfo()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val src = Rect(0, 0, bitmap.width, bitmap.height)
            val dst = Rect(0, 0, width, height)

            repeat(frameCount) {
                val canvas: Canvas = surface.lockCanvas(null)
                try {
                    canvas.drawColor(Color.BLACK)
                    canvas.save()
                    if (mirrored) {
                        canvas.scale(-1f, 1f, width / 2f, height / 2f)
                    }
                    canvas.rotate(rotation, width / 2f, height / 2f)
                    canvas.drawBitmap(bitmap, src, dst, paint)
                    canvas.restore()
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }

                drainEncoder(encoder, muxer, info, false) { track ->
                    trackIndex = track
                    muxerStarted = true
                }
            }

            encoder.signalEndOfInputStream()

            var finished = false
            while (!finished) {
                val result = encoder.dequeueOutputBuffer(info, 10_000)
                when {
                    result == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    result == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted) { "Encoder format changed twice." }
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
                        finished = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        encoder.releaseOutputBuffer(result, false)
                    }
                }
            }
            check(muxerStarted) { "Video encoder did not produce a valid MP4." }
        } finally {
            try { surface?.release() } catch (_: Exception) {}
            try { encoder.stop() } catch (_: Exception) {}
            encoder.release()
            try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
            muxer?.release()
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        info: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onTrackReady: (Int) -> Unit
    ) {
        var track = -1
        var started = false

        while (true) {
            val result = encoder.dequeueOutputBuffer(info, 0)
            when {
                result == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                result == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    started = true
                    onTrackReady(track)
                }
                result >= 0 -> {
                    val buffer = encoder.getOutputBuffer(result)
                    if (buffer != null && info.size > 0 && started) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buffer, info)
                    }
                    val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    encoder.releaseOutputBuffer(result, false)
                    if (eos || endOfStream) return
                }
            }
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
        ) ?: error("Could not create video file.")

        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                FileInputStream(file).use { input -> input.copyTo(out) }
            } ?: error("Could not write video file.")
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }

        return uri
    }

    private fun addButton(
        parent: LinearLayout,
        label: String,
        action: (Button) -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(0, 125, 112))
        }
        button.setOnClickListener { action(button) }
        parent.addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 4, 0, 4)
            }
        )
    }

    private fun createButtonEnabled(view: View, enabled: Boolean) {
        if (view is Button && view.text.toString().contains("CREATE 30-SECOND")) {
            view.isEnabled = enabled
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                createButtonEnabled(view.getChildAt(i), enabled)
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private object MediaCodecInfoCompat {
        const val COLOR_FORMAT_SURFACE = 0x7F000789
    }
}
