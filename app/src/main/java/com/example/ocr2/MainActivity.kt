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
import android.view.inputmethod.EditorInfo
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
    private lateinit var tvExtracted:     TextView
    private lateinit var tvAnswer:        TextView
    private lateinit var tvStatus:        TextView
    private lateinit var tvAiMode:        TextView
    private lateinit var btnCamera:       Button
    private lateinit var btnGallery:      Button
    private lateinit var btnExtract:      Button
    private lateinit var btnAsk:          Button
    private lateinit var progressBar:     ProgressBar
    private lateinit var spinnerLanguage: Spinner
    private lateinit var etQuestion:      EditText


    private var selectedBitmap: Bitmap? = null
    private var cameraImageUri: Uri?    = null
    private var extractedText: String   = ""


    private lateinit var ocrManager:     OcrManager
    private lateinit var gemmaHelper:    GemmaHelper
    private lateinit var keywordHelper:  KeywordSearchHelper

    private val languages = MLKitHelper.Language.values().toList()



    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val bmp = contentResolver.openInputStream(it)?.use { s ->
                BitmapFactory.decodeStream(s)
            }
            setBitmap(bmp)
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val bmp = cameraImageUri?.let { uri ->
                contentResolver.openInputStream(uri)?.use { s ->
                    BitmapFactory.decodeStream(s)
                }
            }
            setBitmap(bmp)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupLanguageSpinner()
        initEngines()
        setupClickListeners()
    }

    private fun bindViews() {
        imageView       = findViewById(R.id.imageView)
        tvImageHint     = findViewById(R.id.tvImageHint)
        tvExtracted     = findViewById(R.id.tvExtracted)
        tvAnswer        = findViewById(R.id.tvAnswer)
        tvStatus        = findViewById(R.id.tvStatus)
        tvAiMode        = findViewById(R.id.tvAiMode)
        btnCamera       = findViewById(R.id.btnCamera)
        btnGallery      = findViewById(R.id.btnGallery)
        btnExtract      = findViewById(R.id.btnExtract)
        btnAsk          = findViewById(R.id.btnAsk)
        progressBar     = findViewById(R.id.progressBar)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)
        etQuestion      = findViewById(R.id.etQuestion)
    }

    private fun setupLanguageSpinner() {
        spinnerLanguage.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            languages.map { it.displayName }
        )
    }

    private fun initEngines() {
        ocrManager    = OcrManager(this)
        gemmaHelper   = GemmaHelper(this)
        keywordHelper = KeywordSearchHelper()

        // Initialize Gemma in background
        tvAiMode.text = "🟡 AI: Loading Gemma model..."
        CoroutineScope(Dispatchers.IO).launch {
            val ready = gemmaHelper.initialize()
            withContext(Dispatchers.Main) {
                tvAiMode.text = if (ready)
                    "🟢 AI: Gemma 2B ready (offline)" else
                    "🟠 AI: Keyword mode (Gemma model not found)"
            }
        }
    }

    private fun setupClickListeners() {
        btnCamera.setOnClickListener  { requestCameraPermission() }
        btnGallery.setOnClickListener { requestStoragePermission() }
        btnExtract.setOnClickListener { extractText() }
        btnAsk.setOnClickListener     { askQuestion() }


        etQuestion.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                askQuestion(); true
            } else false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ocrManager.close()
        gemmaHelper.close()
    }



    private fun extractText() {
        val bitmap = selectedBitmap ?: run {
            toast("Please select or capture an image first")
            return
        }

        val language = languages[spinnerLanguage.selectedItemPosition]
        setLoading(true, "Extracting text with ML Kit + Tesseract...")

        CoroutineScope(Dispatchers.IO).launch {
            val result = ocrManager.extractText(bitmap, language)
            withContext(Dispatchers.Main) {
                extractedText = result
                tvExtracted.text = result
                tvAnswer.text = ""
                tvStatus.text = "✅ Extracted ${result.split("\\s+".toRegex()).size} words"
                setLoading(false)
            }
        }
    }



    private fun askQuestion() {
        val question = etQuestion.text.toString().trim()
        if (question.isBlank()) { toast("Please enter a question"); return }
        if (extractedText.isBlank()) { toast("Please extract text first"); return }

        setLoading(true, "Thinking...")
        tvAnswer.text = ""

        CoroutineScope(Dispatchers.IO).launch {
            val answer = if (gemmaHelper.isReady) {
                // Use Gemma AI for intelligent answer
                val result = gemmaHelper.answer(extractedText, question)
                "🤖 Gemma AI:\n$result"
            } else {
                // Fall back to keyword search
                val result = keywordHelper.answer(extractedText, question)
                "🔍 Keyword Search:\n$result"
            }

            withContext(Dispatchers.Main) {
                tvAnswer.text = answer
                setLoading(false)
                tvStatus.text = "✅ Answer ready"
            }
        }
    }



    private fun setBitmap(bitmap: Bitmap?) {
        if (bitmap != null) {
            selectedBitmap = bitmap
            imageView.setImageBitmap(bitmap)
            tvImageHint.visibility = View.GONE
            extractedText = ""
            tvExtracted.text = ""
            tvAnswer.text = ""
            tvStatus.text = "Image ready — tap Extract Text"
        } else {
            toast("Failed to load image")
        }
    }

    private fun setLoading(loading: Boolean, status: String = "") {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnExtract.isEnabled   = !loading
        btnAsk.isEnabled       = !loading
        btnCamera.isEnabled    = !loading
        btnGallery.isEnabled   = !loading
        if (status.isNotEmpty()) tvStatus.text = status
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()



    private fun requestStoragePermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED)
            galleryLauncher.launch("image/*")
        else ActivityCompat.requestPermissions(this, arrayOf(perm), 101)
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) launchCamera()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 102)
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
            101 -> if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                galleryLauncher.launch("image/*")
            102 -> if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                launchCamera()
        }
    }
}