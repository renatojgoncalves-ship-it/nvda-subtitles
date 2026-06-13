package com.legendas.leitor

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Serviço de acessibilidade que deteta o texto das legendas apresentado pela
 * app Disney+ e o entrega ao [TtsManager] para leitura em voz alta.
 *
 * Estratégia (equivalente móvel da extensão NVDA, que lê o DOM da página):
 *  - O serviço só recebe eventos do Disney+ (restrito em accessibility_service_config).
 *  - A cada alteração de conteúdo, percorre a árvore de acessibilidade da janela
 *    ativa e recolhe os nós de texto situados na zona inferior do ecrã (onde
 *    aparecem as legendas).
 *  - Junta as linhas, ignora repetições e fala o resultado.
 *
 * Nota: se a app de vídeo desenhar as legendas diretamente sobre a superfície
 * de vídeo (sem as expor na árvore de acessibilidade), não há texto para ler —
 * é uma limitação inerente a esta abordagem.
 */
class SubtitleAccessibilityService : AccessibilityService() {

    companion object {
        private const val DISNEY_PACKAGE = "com.disney.disneyplus"
        private const val MIN_CHARS = 2
    }

    private var tts: TtsManager? = null
    private var ultimaLegenda: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        tts = TtsManager(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName?.toString() != DISNEY_PACKAGE) return
        if (!leituraAtiva) return

        val raiz = rootInActiveWindow ?: return
        try {
            val legenda = extrairLegenda(raiz)
            if (!legenda.isNullOrBlank()) anunciar(legenda)
        } finally {
            @Suppress("DEPRECATION")
            raiz.recycle()
        }
    }

    override fun onInterrupt() {
        tts?.parar()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        tts?.libertar()
        tts = null
        return super.onUnbind(intent)
    }

    /** Recolhe e compõe o texto da legenda a partir da árvore de nós. */
    private fun extrairLegenda(raiz: AccessibilityNodeInfo): String? {
        val alturaEcra = resources.displayMetrics.heightPixels
        // Limite superior da zona de legendas (ex.: zona = 0.45 → topo a 55% da altura).
        val limiteSuperior = alturaEcra * (1f - zonaLegendas)

        val candidatos = ArrayList<Pair<Rect, String>>()
        recolherTexto(raiz, candidatos)
        if (candidatos.isEmpty()) return null

        // Fica com o texto cujo centro vertical está na zona inferior;
        // se nada cair na zona, usa todos os candidatos (tolerante a ecrãs diferentes).
        val naZona = candidatos.filter { it.first.centerY() >= limiteSuperior }
        val selecionados = naZona.ifEmpty { candidatos }

        // Ordena de cima para baixo / esquerda para direita e junta as linhas.
        return selecionados
            .sortedWith(compareBy({ it.first.top }, { it.first.left }))
            .joinToString(" ") { it.second.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { null }
    }

    private fun recolherTexto(
        node: AccessibilityNodeInfo?,
        out: MutableList<Pair<Rect, String>>
    ) {
        node ?: return
        val texto = node.text?.toString()?.trim()
        if (!texto.isNullOrEmpty() && pareceLegenda(node, texto)) {
            val r = Rect()
            node.getBoundsInScreen(r)
            if (!r.isEmpty) out.add(r to texto)
        }
        for (i in 0 until node.childCount) {
            val filho = node.getChild(i)
            recolherTexto(filho, out)
            @Suppress("DEPRECATION")
            filho?.recycle()
        }
    }

    /** Heurística para distinguir legendas de controlos da interface. */
    private fun pareceLegenda(node: AccessibilityNodeInfo, texto: String): Boolean {
        if (node.isClickable || node.isEditable || node.isCheckable) return false
        if (texto.length < MIN_CHARS) return false
        // Tem de conter letras (exclui contadores de tempo "12:34", percentagens, etc.).
        return texto.any { it.isLetter() }
    }

    private fun anunciar(legenda: String) {
        if (legenda == ultimaLegenda) return
        ultimaLegenda = legenda
        tts?.falar(legenda)
    }
}
