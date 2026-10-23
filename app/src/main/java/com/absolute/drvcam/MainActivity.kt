
package com.absolute.drvcam

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var preview: ImageView
    private var selectedImage: Uri? = null
    private var rotation = 0f
    private var mirrored = false
    private var looping = false

    private val imagePicker =
        registerForActivityResult(
            android.app.ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                selectedImage = uri
                preview.setImageURI(uri)
                preview.visibility = View.VISIBLE
                Toast.makeText(
                    this,
                    "Photo selected",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.rgb(12, 19, 25))
        }

        val title = TextView(this).apply {
            text = "ABSOLUTE DRVCAM"
            textSize = 26f
            setTextColor(Color.rgb(70, 220, 195))
            gravity = Gravity.CENTER
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "PHOTO MEDIA STUDIO"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        root.addView(subtitle)

        preview = ImageView(this).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
            background.setTint(Color.rgb(28, 39, 46))
        }

        root.addView(
            preview,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                setMargins(0, 24, 0, 20)
            }
        )

        addButton(root, "SELECT PHOTO") {
            imagePicker.launch("image/*")
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

        addButton(root, "SELECT TARGET APP") {
            Toast.makeText(
                this,
                "Target-app selection will be added in a later version.",
                Toast.LENGTH_LONG
            ).show()
        }

        val note = TextView(this).apply {
            text = "Photo preview and controls"
            textSize = 12f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }
        root.addView(note)

        setContentView(root)
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
                setMargins(0, 5, 0, 5)
            }
        )
    }
}
