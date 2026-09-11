package com.wesley.vozclone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.snackbar.Snackbar
import com.wesley.vozclone.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: android.content.SharedPreferences

    private var referenceVoiceFile: File? = null
    private var resultAudioFile: File? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var mediaPlayer: MediaPlayer? = null

    private val pickAudioLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) copyPickedAudioToApp(uri)
    }

    private val requestRecordPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecording()
        } else {
            toast(getString(R.string.perm_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        prefs = getSharedPreferences("voz_clone_prefs", Context.MODE_PRIVATE)
        binding.editServerUrl.setText(prefs.getString("server_url", ""))

        binding.btnAttach.setOnClickListener {
            pickAudioLauncher.launch("audio/*")
        }

        binding.btnRecord.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                ensureRecordPermissionAndStart()
            }
        }

        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }

        binding.btnGenerate.setOnClickListener {
            generateSpeech()
        }

        binding.btnPlay.setOnClickListener {
            playResult()
        }

        binding.btnSave.setOnClickListener {
            saveResultToDownloads()
        }

        binding.btnShare.setOnClickListener {
            shareResult()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        mediaPlayer?.release()
    }

    // ---------------- Anexar arquivo ----------------

    private fun copyPickedAudioToApp(uri: Uri) {
        try {
            val name = queryFileName(uri) ?: "voz_referencia.wav"
            val destDir = File(cacheDir, "audio").apply { mkdirs() }
            val destFile = File(destDir, "ref_${System.currentTimeMillis()}_${sanitize(name)}")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            referenceVoiceFile = destFile
            binding.tvVoiceStatus.text = "Voz anexada: ${destFile.name} (${destFile.length() / 1024} KB)"
        } catch (e: Exception) {
            toast("Erro ao anexar áudio: ${e.message}")
        }
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(idx)
            }
        }
        return name
    }

    private fun sanitize(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    // ---------------- Gravar voz ----------------

    private fun ensureRecordPermissionAndStart() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startRecording()
        } else {
            requestRecordPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        try {
            val destDir = File(cacheDir, "audio").apply { mkdirs() }
            val destFile = File(destDir, "gravacao_${System.currentTimeMillis()}.m4a")

            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(destFile.absolutePath)
                prepare()
                start()
            }

            referenceVoiceFile = destFile
            isRecording = true
            binding.btnRecord.text = getString(R.string.btn_stop_record)
            binding.tvVoiceStatus.text = "Gravando… fale por 10-20 segundos para melhor qualidade"
        } catch (e: Exception) {
            toast("Erro ao iniciar gravação: ${e.message}")
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
        } finally {
            mediaRecorder = null
            isRecording = false
            binding.btnRecord.text = getString(R.string.btn_record)
            val f = referenceVoiceFile
            if (f != null && f.exists()) {
                binding.tvVoiceStatus.text = "Voz gravada: ${f.name} (${f.length() / 1024} KB)"
            }
        }
    }

    // ---------------- Servidor ----------------

    private fun currentServerUrl(): String {
        val url = binding.editServerUrl.text?.toString()?.trim().orEmpty()
        prefs.edit().putString("server_url", url).apply()
        return url
    }

    private fun testConnection() {
        val url = currentServerUrl()
        if (url.isEmpty()) {
            toast(getString(R.string.hint_server))
            return
        }
        setStatus(getString(R.string.status_testing), true)
        CoroutineScope(Dispatchers.Main).launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    VoiceApiClient(url).checkHealth()
                } catch (e: Exception) {
                    false
                }
            }
            setStatus(
                if (ok) getString(R.string.status_connected) else getString(R.string.status_disconnected),
                false
            )
        }
    }

    // ---------------- Gerar fala ----------------

    private fun generateSpeech() {
        val text = binding.editText.text?.toString()?.trim().orEmpty()
        val url = currentServerUrl()
        val voice = referenceVoiceFile

        if (text.isEmpty()) {
            toast("Digite um texto primeiro")
            return
        }
        if (voice == null || !voice.exists()) {
            toast("Anexe ou grave uma voz de referência primeiro")
            return
        }
        if (url.isEmpty()) {
            toast("Configure o endereço do servidor")
            return
        }

        setStatus(getString(R.string.status_generating), true)
        binding.btnGenerate.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    VoiceApiClient(url).generateSpeech(text, "pt", voice)
                }
                val outDir = File(cacheDir, "audio").apply { mkdirs() }
                val outFile = File(outDir, "resultado_${System.currentTimeMillis()}.wav")
                FileOutputStream(outFile).use { it.write(bytes) }
                resultAudioFile = outFile

                setStatus(getString(R.string.status_success), false)
                binding.btnPlay.isEnabled = true
                binding.btnSave.isEnabled = true
                binding.btnShare.isEnabled = true
            } catch (e: Exception) {
                setStatus(getString(R.string.status_error, e.message ?: "desconhecido"), false)
            } finally {
                binding.btnGenerate.isEnabled = true
            }
        }
    }

    // ---------------- Reproduzir / salvar / compartilhar ----------------

    private fun playResult() {
        val file = resultAudioFile ?: return
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            toast("Erro ao reproduzir: ${e.message}")
        }
    }

    private fun saveResultToDownloads() {
        val file = resultAudioFile ?: return
        try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloads.exists()) downloads.mkdirs()
            val destFile = File(downloads, "voz_clone_${System.currentTimeMillis()}.wav")
            file.copyTo(destFile, overwrite = true)
            Snackbar.make(binding.root, "Salvo em Downloads/${destFile.name}", Snackbar.LENGTH_LONG).show()
        } catch (e: Exception) {
            toast("Erro ao salvar: ${e.message}")
        }
    }

    private fun shareResult() {
        val file = resultAudioFile ?: return
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Compartilhar áudio"))
        } catch (e: Exception) {
            toast("Erro ao compartilhar: ${e.message}")
        }
    }

    // ---------------- Utils ----------------

    private fun setStatus(text: String, loading: Boolean) {
        binding.tvStatus.text = text
        binding.progressBar.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
