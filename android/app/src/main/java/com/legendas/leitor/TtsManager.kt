package com.legendas.leitor

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Encapsula o [TextToSpeech]: inicialização assíncrona, aplicação das
 * preferências (velocidade/tom/idioma/volume) e leitura das legendas.
 *
 * Frases pedidas antes de o motor estar pronto ficam em fila e são lidas
 * assim que a inicialização termina.
 *
 * Volume: cada leitura usa [TextToSpeech.Engine.KEY_PARAM_VOLUME] (0.1–1.0)
 * para ajustar o volume da voz face ao conteúdo. Opcionalmente, enquanto fala,
 * pede "audio focus" com ducking para baixar temporariamente o som do Disney+.
 */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private var pronto = false
    private val filaPendente = ArrayDeque<String>()

    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var pedidoFoco: AudioFocusRequest? = null
    private var focoAtivo = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        aplicarPreferencias()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = largarFocoSeParado()
            override fun onError(utteranceId: String?) = largarFocoSeParado()
            override fun onError(utteranceId: String?, errorCode: Int) = largarFocoSeParado()
        })
        pronto = true
        while (filaPendente.isNotEmpty()) {
            falarAgora(filaPendente.removeFirst())
        }
    }

    /** Relê velocidade/tom/idioma a partir das preferências. */
    fun aplicarPreferencias() {
        val motor = tts ?: return
        motor.setSpeechRate(appContext.velocidade)
        motor.setPitch(appContext.tom)
        val locale = Locale.forLanguageTag(appContext.idioma)
        val resultado = motor.setLanguage(locale)
        if (resultado == TextToSpeech.LANG_MISSING_DATA ||
            resultado == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            // Idioma indisponível no dispositivo → recorre ao idioma do sistema.
            motor.language = Locale.getDefault()
        }
    }

    fun falar(texto: String) {
        if (!pronto) {
            filaPendente.addLast(texto)
            return
        }
        falarAgora(texto)
    }

    private fun falarAgora(texto: String) {
        val modo =
            if (appContext.interromper) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        if (appContext.baixarConteudo) pedirFoco()
        val params = Bundle().apply {
            putFloat(
                TextToSpeech.Engine.KEY_PARAM_VOLUME,
                appContext.volume.coerceIn(0.1f, 1.0f)
            )
        }
        tts?.speak(texto, modo, params, "legenda-${System.nanoTime()}")
    }

    fun parar() {
        tts?.stop()
        largarFoco()
    }

    fun libertar() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        pronto = false
        largarFoco()
    }

    /** Pede audio focus transitório com ducking, baixando o som do Disney+. */
    private fun pedirFoco() {
        if (focoAtivo) return
        val am = audioManager ?: return
        val pedido = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        pedidoFoco = pedido
        am.requestAudioFocus(pedido)
        focoAtivo = true
    }

    /** Liberta o audio focus apenas se já não estiver a falar. */
    private fun largarFocoSeParado() {
        if (tts?.isSpeaking == true) return
        largarFoco()
    }

    private fun largarFoco() {
        if (!focoAtivo) return
        val am = audioManager
        val pedido = pedidoFoco
        if (am != null && pedido != null) am.abandonAudioFocusRequest(pedido)
        pedidoFoco = null
        focoAtivo = false
    }
}
