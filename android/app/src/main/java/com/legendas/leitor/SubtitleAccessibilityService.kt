package com.legendas.leitor

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Serviço de acessibilidade que deteta o texto das legendas apresentado pela
 * app Disney+ e o entrega ao [TtsManager] para leitura em voz alta.
 *
 * Estratégia (equivalente móvel da extensão NVDA, que sondava o DOM da página):
 *
 *  - A view das legendas do Disney+ **não é uma "live region"**, por isso alterar
 *    o texto não gera um evento de acessibilidade fiável. O TalkBack só as lê
 *    quando o utilizador move o foco manualmente. Para ler em automático é
 *    preciso **sondar (polling)** a árvore periodicamente, em vez de esperar por
 *    eventos.
 *  - As legendas costumam estar numa **janela separada** (overlay do player),
 *    pelo que se percorrem **todas as janelas** ([getWindows]) e não apenas a
 *    janela ativa ([rootInActiveWindow]) — é assim que o TalkBack lhes chega.
 *  - Em cada sondagem recolhem-se os nós de texto da app Disney+ na zona inferior
 *    do ecrã, juntam-se as linhas, ignoram-se repetições e fala-se o resultado.
 *
 * O polling só está ativo enquanto o Disney+ está em primeiro plano: pára-se
 * automaticamente quando o Disney+ deixa de ter janelas visíveis (poupa bateria)
 * e recomeça-se quando volta a primeiro plano.
 */
class SubtitleAccessibilityService : AccessibilityService() {

    companion object {
        private const val DISNEY_PACKAGE = "com.disney.disneyplus"
        private const val MIN_CHARS = 2

        /** Intervalo entre sondagens da árvore de acessibilidade. */
        private const val INTERVALO_MS = 300L

        /** Sondagens consecutivas sem Disney+ visível antes de parar o polling. */
        private const val MAX_AUSENCIAS = 10
    }

    private val handler = Handler(Looper.getMainLooper())
    private var tts: TtsManager? = null
    private var ultimaLegenda: String = ""

    private var aPollar = false
    private var ausenciasSeguidas = 0

    private val tarefaPoll = object : Runnable {
        override fun run() {
            var disneyPresente = false
            if (leituraAtiva) {
                val legenda = extrairLegendaDasJanelas { disneyPresente = true }
                if (!legenda.isNullOrBlank()) anunciar(legenda)
            } else {
                disneyPresente = haJanelaDisney()
            }

            // Pára o polling se o Disney+ deixou de estar em primeiro plano.
            if (disneyPresente) {
                ausenciasSeguidas = 0
            } else if (++ausenciasSeguidas >= MAX_AUSENCIAS) {
                pararPoll()
                return
            }
            handler.postDelayed(this, INTERVALO_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        tts = TtsManager(this)
        iniciarPoll()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Os eventos servem apenas para (re)acordar o polling quando o Disney+
        // volta a primeiro plano; a leitura em si é feita pela sondagem.
        if (event?.packageName?.toString() == DISNEY_PACKAGE) iniciarPoll()
    }

    override fun onInterrupt() {
        tts?.parar()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        pararPoll()
        tts?.libertar()
        tts = null
        return super.onUnbind(intent)
    }

    private fun iniciarPoll() {
        ausenciasSeguidas = 0
        if (aPollar) return
        aPollar = true
        handler.post(tarefaPoll)
    }

    private fun pararPoll() {
        aPollar = false
        handler.removeCallbacks(tarefaPoll)
    }

    /**
     * Percorre todas as janelas do Disney+ e compõe o texto da legenda.
     * Invoca [marcarPresente] se encontrar pelo menos uma janela do Disney+.
     */
    private fun extrairLegendaDasJanelas(marcarPresente: () -> Unit): String? {
        val candidatos = ArrayList<Pair<Rect, String>>()

        for (janela in windows) {
            val raiz = janela.root ?: continue
            try {
                if (raiz.packageName?.toString() != DISNEY_PACKAGE) continue
                marcarPresente()
                recolherTexto(raiz, candidatos)
            } finally {
                @Suppress("DEPRECATION")
                raiz.recycle()
            }
        }

        // Recurso: algumas versões não expõem o overlay em getWindows().
        if (candidatos.isEmpty()) {
            rootInActiveWindow?.let { raiz ->
                try {
                    if (raiz.packageName?.toString() == DISNEY_PACKAGE) {
                        marcarPresente()
                        recolherTexto(raiz, candidatos)
                    }
                } finally {
                    @Suppress("DEPRECATION")
                    raiz.recycle()
                }
            }
        }

        if (candidatos.isEmpty()) return null
        return compor(candidatos)
    }

    /** Verifica (sem recolher texto) se há alguma janela do Disney+ visível. */
    private fun haJanelaDisney(): Boolean {
        for (janela in windows) {
            val raiz = janela.root ?: continue
            val ePackageDisney = raiz.packageName?.toString() == DISNEY_PACKAGE
            @Suppress("DEPRECATION")
            raiz.recycle()
            if (ePackageDisney) return true
        }
        return false
    }

    /** Filtra pela zona inferior do ecrã, ordena e junta as linhas. */
    private fun compor(candidatos: List<Pair<Rect, String>>): String? {
        val alturaEcra = resources.displayMetrics.heightPixels
        // Limite superior da zona de legendas (ex.: zona = 0.45 → topo a 55% da altura).
        val limiteSuperior = alturaEcra * (1f - zonaLegendas)

        val naZona = candidatos.filter { it.first.centerY() >= limiteSuperior }
        val selecionados = naZona.ifEmpty { candidatos }

        return selecionados
            .sortedWith(compareBy({ it.first.top }, { it.first.left }))
            .map { it.second.trim() }
            .distinct()
            .joinToString(" ")
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
