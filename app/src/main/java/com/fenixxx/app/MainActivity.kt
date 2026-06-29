package com.fenixxx.app

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fenixxx.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import androidx.core.content.ContextCompat
import android.content.ClipboardManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var textToSpeech: TextToSpeech
    private var isMonitoring = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale("es", "ES")
            }
        }

        setupSpinner()
        setupButton()
        setupClipboardMonitor()
        updateUI()
    }

    private fun setupSpinner() {
        ArrayAdapter.createFromResource(
            this,
            R.array.languages_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerLanguage.adapter = adapter
        }
    }

    private fun setupButton() {
        binding.buttonToggle.setOnClickListener {
            isMonitoring = !isMonitoring
            updateUI()
        }
    }

    private fun setupClipboardMonitor() {
        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    }

    override fun onResume() {
        super.onResume()
        if (isMonitoring) {
            clipboardManager.addPrimaryClipChangedListener(clipListener)
        }
    }

    override fun onPause() {
        super.onPause()
        clipboardManager.removePrimaryClipChangedListener(clipListener)
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (isMonitoring) {
            val item = clipboardManager.primaryClip?.getItemAt(0)
            val text = item?.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty() && text != viewModel.lastTranslatedText) {
                translateText(text)
            }
        }
    }

    private fun translateText(text: String) {
        if (text.length > 500) {
            Toast.makeText(this, R.string.error_text_too_long, Toast.LENGTH_SHORT).show()
            return
        }
        val sourceLang = when (binding.spinnerLanguage.selectedItemPosition) {
            0 -> "fr"
            1 -> "en"
            2 -> "it"
            3 -> "pt"
            4 -> "de"
            else -> "fr"
        }
        val url = "https://api.mymemory.translated.net/get?q=$text&langpair=$sourceLang|es"
        viewModel.translate(url) { translatedText ->
            if (translatedText != null) {
                viewModel.lastTranslatedText = text
                binding.textViewOriginal.text = text
                binding.textViewTranslated.text = translatedText
                speakOut(translatedText)
            } else {
                Toast.makeText(this, R.string.error_translation_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun speakOut(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun updateUI() {
        binding.buttonToggle.text = if (isMonitoring) getString(R.string.pause) else getString(R.string.resume)
        binding.indicatorStatus.setBackgroundColor(
            if (isMonitoring) ContextCompat.getColor(this, R.color.green)
            else ContextCompat.getColor(this, R.color.red)
        )
    }

    override fun onDestroy() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }
}

class MainViewModel : ViewModel() {
    var lastTranslatedText = ""

    fun translate(url: String, callback: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                    val jsonObject = JSONObject(response.body!!.string())
                    val translatedText = jsonObject.getJSONObject("responseData").getString("translatedText")
                    callback.invoke(translatedText)
                }
            } catch (e: Exception) {
                callback.invoke(null)
            }
        }
    }
}