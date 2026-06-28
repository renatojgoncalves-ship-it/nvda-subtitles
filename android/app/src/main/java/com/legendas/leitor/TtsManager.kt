package com.legendas.leitor

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Encapsula o [TextToSpeech]: inicialização assíncrona, preferências
 * (velocidade/tom/idioma/volume) e leitura das legendas em voz alta.
 *
 * Para a voz se ouvir CLARAMENTE acima do filme:
 *  - A voz é encaminhada para o **canal de acessibilidade** (independente do
 *    volume de multimédia do Disney+) e, enquanto fala, esse canal é colocado
 *    no **máximo**.
 *  - Enquanto fala, baixa-se o áudio do filme ("ducking" via audio focus).
 *  - O controlo de volume vai de 10% a 200%: até 100% ajusta o volume da voz;
 *    acima de 100% **baixa progressivamente o som do filme**, para a voz ficar
 *    mais de 100% acima do áudio do programa.
 *
 * As alterações de volume são repostas pouco depois de a leitura parar.
 */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private var pronto = false
    private val filaPendente = ArrayDeque<String>()

    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val handler = Handler(Looper.getMainLooper())

    private var pedidoFoco: AudioFocusRequest? = null
    private var ativo = false
    private var volAccSalvo = -1
    private var volMusicSalvo = -1

    private val restaurarRunnable = Runnable { restaurarAudio() }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        // Encaminha a voz para o canal de acessibilidade (separado do filme).
        tts?.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        aplicarPreferencias()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = aoFimDaFala()
            override fun onError(utteranceId: String?) = aoFimDaFala()
            override fun onError(utteranceId: String?, errorCode: Int) = aoFimDaFala()
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
        aoComecarFala()
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
        handler.removeCallbacks(restaurarRunnable)
        restaurarAudio()
    }

    fun libertar() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        pronto = false
        handler.removeCallbacks(restaurarRunnable)
        restaurarAudio()
    }

    // ---------------------------------------------------------------------
    // Gestão do volume voz/filme durante a fala
    // ---------------------------------------------------------------------

    private fun aoComecarFala() {
        handler.removeCallbacks(restaurarRunnable)
        if (ativo) return
        ativo = true
        val am = audioManager ?: return

        // Voz no máximo (canal de acessibilidade).
        runCatching {
            volAccSalvo = am.getStreamVolume(AudioManager.STREAM_ACCESSIBILITY)
            am.setStreamVolume(
                AudioManager.STREAM_ACCESSIBILITY,
                am.getStreamMaxVolume(AudioManager.STREAM_ACCESSIBILITY),
                0
            )
        }

        if (appContext.baixarConteudo) {
            // Baixa o filme enquanto a voz fala (ducking).
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            pedidoFoco = req
            runCatching { am.requestAudioFocus(req) }

            // Acima de 100%, baixa ainda mais o som do filme.
            val v = appContext.volume
            if (v > 1.0f) {
                runCatching {
                    volMusicSalvo = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val fator = (2.0f - v).coerceIn(0f, 1f)
                    am.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        (volMusicSalvo * fator).toInt(),
                        0
                    )
                }
            }
        }
    }

    /** Quando a fala termina, repõe o áudio pouco depois (evita "saltos" entre legendas). */
    private fun aoFimDaFala() {
        if (tts?.isSpeaking == true) return
        handler.postDelayed(restaurarRunnable, 700)
    }

    private fun restaurarAudio() {
        val am = audioManager
        if (am != null) {
            runCatching { if (volMusicSalvo >= 0) am.setStreamVolume(AudioManager.STREAM_MUSIC, volMusicSalvo, 0) }
            runCatching { if (volAccSalvo >= 0) am.setStreamVolume(AudioManager.STREAM_ACCESSIBILITY, volAccSalvo, 0) }
            pedidoFoco?.let { r -> runCatching { am.abandonAudioFocusRequest(r) } }
        }
        volMusicSalvo = -1
        volAccSalvo = -1
        pedidoFoco = null
        ativo = false
    }
}
