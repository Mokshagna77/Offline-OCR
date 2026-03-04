package com.example.ocr2

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var imageView:       ImageView
    private lateinit var tvImageHint:     TextView
    private lateinit var tvResult:        TextView
    private lateinit var tvStatus:        TextView
    private lateinit var btnGallery:      Button
    private lateinit var btnCamera:       Button
    private lateinit var btnRecognize:    Button
    private lateinit var btnCopy:         Button
    private lateinit var progressBar:     ProgressBar
    private lateinit var spinnerLanguage: Spinner
    private lateinit var checkBoth:       CheckBox

    private var selectedBitmap: Bitmap? = null
    private var cameraImageUri: Uri?    = null
    private lateinit var ocrManager:    OcrManager

    private val languages = MLKitHelper.Language.values().toList()

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val bitmap = contentResolver.openInputStream(it)?.use { s ->
                BitmapFactory.decodeStream(s)
            }
            setBitmap(bitmap, "Image loaded")
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val bitmap = cameraImageUri?.let { uri ->
                contentResolver.openInputStream(uri)?.use { s ->
                    BitmapFactory.decodeStream(s)
                }
            }
            setBitmap(bitmap, "Photo captured")
        } else {
            Toast.makeText(this, "Camera cancelled", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView       = findViewById(R.id.imageView)
        tvImageHint     = findViewById(R.id.tvImageHint)
        tvResult        = findViewById(R.id.tvResult)
        tvStatus        = findViewById(R.id.tvStatus)
        btnGallery      = findViewById(R.id.btnGallery)
        btnCamera       = findViewById(R.id.btnCamera)
        btnRecognize    = findViewById(R.id.btnRecognize)
        btnCopy         = findViewById(R.id.btnCopy)
        progressBar     = findViewById(R.id.progressBar)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)
        checkBoth       = findViewById(R.id.checkBothEngines)

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            languages.map { it.displayName }
        )
        spinnerLanguage.adapter = adapter

        ocrManager = OcrManager(this)

        btnGallery.setOnClickListener   { requestStoragePermission() }
        btnCamera.setOnClickListener    { requestCameraPermission() }
        btnRecognize.setOnClickListener { recognizeText() }
        btnCopy.setOnClickListener      { copyToClipboard() }
    }

    override fun onDestroy() {
        super.onDestroy()
        ocrManager.close()
    }

    private fun recognizeText() {
        val bitmap = selectedBitmap ?: run {
            Toast.makeText(this, "Please select or capture an image first", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedLanguage = languages[spinnerLanguage.selectedItemPosition]
        val useBoth = checkBoth.isChecked

        setProcessing(true)
        tvStatus.text = "Running OCR with ${selectedLanguage.name}" +
                if (useBoth && selectedLanguage == MLKitHelper.Language.LATIN) " + Tesseract" else ""

        CoroutineScope(Dispatchers.IO).launch {
            val result = ocrManager.extractText(bitmap, selectedLanguage, useBoth)
            withContext(Dispatchers.Main) {
                tvResult.text = result
                btnCopy.visibility = if (result != "No text detected") View.VISIBLE else View.GONE
                tvStatus.text = "Done — ${result.split("\\s+".toRegex()).size} words detected"
                setProcessing(false)
            }
        }
    }

    private fun setBitmap(bitmap: Bitmap?, status: String) {
        if (bitmap != null) {
            selectedBitmap = bitmap
            imageView.setImageBitmap(bitmap)
            tvImageHint.visibility = View.GONE
            tvResult.text = ""
            btnCopy.visibility = View.GONE
            tvStatus.text = "$status — tap Extract Text"
        } else {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setProcessing(processing: Boolean) {
        progressBar.visibility = if (processing) View.VISIBLE else View.GONE
        btnRecognize.isEnabled = !processing
        btnGallery.isEnabled   = !processing
        btnCamera.isEnabled    = !processing
    }

    private fun copyToClipboard() {
        val text = tvResult.text.toString()
        if (text.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OCR Result", text))
        Toast.makeText(this, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    private fun requestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED)
            galleryLauncher.launch("image/*")
        else
            ActivityCompat.requestPermissions(this, arrayOf(permission), 101)
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) launchCamera()
        else
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 102)
    }

    private fun launchCamera() {
        val file = File(cacheDir, "ocr_${System.currentTimeMillis()}.jpg")
        cameraImageUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        cameraLauncher.launch(cameraImageUri!!)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            101 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    galleryLauncher.launch("image/*")
                else Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
            }
            102 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    launchCamera()
                else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

