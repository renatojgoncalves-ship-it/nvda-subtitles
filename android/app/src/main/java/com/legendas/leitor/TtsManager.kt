package com.legendas.leitor

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Encapsula o [TextToSpeech]: inicialização assíncrona, aplicação das
 * preferências (velocidade/tom/idioma) e leitura das legendas.
 *
 * Frases pedidas antes de o motor estar pronto ficam em fila e são lidas
 * assim que a inicialização termina.
 */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private var pronto = false
    private val filaPendente = ArrayDeque<String>()

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        aplicarPreferencias()
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
        tts?.speak(texto, modo, null, "legenda-${System.nanoTime()}")
    }

    fun parar() {
        tts?.stop()
    }

    fun libertar() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        pronto = false
    }
}
