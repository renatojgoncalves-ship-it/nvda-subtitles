package com.legendas.leitor

import android.content.Context

/**
 * Acesso centralizado às preferências do utilizador (SharedPreferences),
 * exposto como propriedades de extensão sobre [Context]. Sem dependências
 * externas e com valores por omissão sensatos.
 */
private const val PREFS_FILE = "legendas_prefs"

private const val KEY_LEITURA = "leitura_ativa"
private const val KEY_INTERROMPER = "interromper"
private const val KEY_VELOCIDADE = "velocidade"
private const val KEY_TOM = "tom"
private const val KEY_VOLUME = "volume"
private const val KEY_BAIXAR_CONTEUDO = "baixar_conteudo"
private const val KEY_IDIOMA = "idioma"
private const val KEY_ZONA = "zona_legendas"
private const val KEY_LEITOR_ECRA = "usar_leitor_ecra"

private fun Context.prefs() =
    applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

var Context.leituraAtiva: Boolean
    get() = prefs().getBoolean(KEY_LEITURA, true)
    set(v) = prefs().edit().putBoolean(KEY_LEITURA, v).apply()

var Context.interromper: Boolean
    get() = prefs().getBoolean(KEY_INTERROMPER, true)
    set(v) = prefs().edit().putBoolean(KEY_INTERROMPER, v).apply()

var Context.velocidade: Float
    get() = prefs().getFloat(KEY_VELOCIDADE, 1.0f)
    set(v) = prefs().edit().putFloat(KEY_VELOCIDADE, v).apply()

var Context.tom: Float
    get() = prefs().getFloat(KEY_TOM, 1.0f)
    set(v) = prefs().edit().putFloat(KEY_TOM, v).apply()

/**
 * Volume da voz (0.1–1.0) aplicado a cada leitura, relativo ao volume de
 * multimédia do dispositivo. 1.0 → volume máximo da voz.
 */
var Context.volume: Float
    get() = prefs().getFloat(KEY_VOLUME, 1.0f)
    set(v) = prefs().edit().putFloat(KEY_VOLUME, v).apply()

/**
 * Se verdadeiro, baixa temporariamente o som do Disney+ (audio focus com
 * "ducking") enquanto a legenda é lida, para a voz se ouvir por cima do vídeo.
 */
var Context.baixarConteudo: Boolean
    get() = prefs().getBoolean(KEY_BAIXAR_CONTEUDO, true)
    set(v) = prefs().edit().putBoolean(KEY_BAIXAR_CONTEUDO, v).apply()

/** Idioma da voz no formato BCP-47 (ex.: "pt-PT", "en-US"). */
var Context.idioma: String
    get() = prefs().getString(KEY_IDIOMA, "pt-PT") ?: "pt-PT"
    set(v) = prefs().edit().putString(KEY_IDIOMA, v).apply()

/**
 * Fração inferior do ecrã onde se procuram as legendas (0.2–0.8).
 * 0.45 → considera-se legenda o texto cujo centro está nos 45% de baixo.
 */
var Context.zonaLegendas: Float
    get() = prefs().getFloat(KEY_ZONA, 0.45f)
    set(v) = prefs().edit().putFloat(KEY_ZONA, v).apply()

/**
 * Se verdadeiro, as legendas são lidas pela voz do leitor de ecrã (TalkBack),
 * em vez da voz própria da app — volume e voz controlados pelo TalkBack.
 */
var Context.usarLeitorEcra: Boolean
    get() = prefs().getBoolean(KEY_LEITOR_ECRA, true)
    set(v) = prefs().edit().putBoolean(KEY_LEITOR_ECRA, v).apply()
