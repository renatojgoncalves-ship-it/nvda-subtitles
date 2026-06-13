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
private const val KEY_IDIOMA = "idioma"
private const val KEY_ZONA = "zona_legendas"

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
