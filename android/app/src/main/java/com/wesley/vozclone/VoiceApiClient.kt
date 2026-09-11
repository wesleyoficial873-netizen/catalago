package com.wesley.vozclone

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP simples para conversar com o servidor de clonagem de voz
 * (ex.: servidor Coqui XTTS rodando no Google Colab + ngrok, ou em qualquer
 * outro host que implemente os mesmos endpoints).
 *
 * Endpoints esperados no servidor:
 *   GET  {baseUrl}/health           -> 200 OK se estiver no ar
 *   POST {baseUrl}/tts              -> multipart/form-data:
 *        campo "text"      (texto a ser falado)
 *        campo "language"  (ex: "pt")
 *        campo "voice"     (arquivo de áudio de referência, wav/mp3/m4a)
 *        resposta: bytes de áudio (audio/wav)
 */
class VoiceApiClient(private val baseUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    private fun normalizedBase(): String {
        var url = baseUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        if (url.endsWith("/")) url = url.dropLast(1)
        return url
    }

    @Throws(IOException::class)
    fun checkHealth(): Boolean {
        val request = Request.Builder()
            .url("${normalizedBase()}/health")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            return response.isSuccessful
        }
    }

    @Throws(IOException::class)
    fun generateSpeech(text: String, language: String, voiceFile: File): ByteArray {
        val mediaType = guessAudioMediaType(voiceFile.name)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("text", text)
            .addFormDataPart("language", language)
            .addFormDataPart(
                "voice",
                voiceFile.name,
                voiceFile.asRequestBody(mediaType)
            )
            .build()

        val request = Request.Builder()
            .url("${normalizedBase()}/tts")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: "sem detalhes"
                throw IOException("Servidor respondeu ${response.code}: $errBody")
            }
            return response.body?.bytes() ?: throw IOException("Resposta vazia do servidor")
        }
    }

    private fun guessAudioMediaType(fileName: String) =
        when {
            fileName.endsWith(".wav", true) -> "audio/wav".toMediaTypeOrNull()
            fileName.endsWith(".mp3", true) -> "audio/mpeg".toMediaTypeOrNull()
            fileName.endsWith(".m4a", true) -> "audio/mp4".toMediaTypeOrNull()
            fileName.endsWith(".ogg", true) -> "audio/ogg".toMediaTypeOrNull()
            else -> "application/octet-stream".toMediaTypeOrNull()
        }
}
