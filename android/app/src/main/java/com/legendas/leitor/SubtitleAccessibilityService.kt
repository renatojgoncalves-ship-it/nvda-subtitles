package com.legendas.leitor

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Serviço de acessibilidade que deteta o texto das legendas do Disney+ e o lê
 * em voz alta.
 *
 * Duas formas de leitura (configurável):
 *  - **Voz do leitor de ecrã (TalkBack)** — predefinição. O texto é anunciado
 *    através de um pequeno overlay com [View.announceForAccessibility], pelo que
 *    é o TalkBack a falar, com a sua voz, volume e "ducking". Uma só voz, volume
 *    controlado pelo TalkBack, sem a app mexer em volumes do sistema.
 *  - **Voz própria da app** — alternativa (quando o TalkBack está desligado),
 *    usando o [TtsManager].
 *
 * A deteção das legendas é igual nos dois casos: sonda-se periodicamente a
 * árvore de acessibilidade de todas as janelas do Disney+.
 */
class SubtitleAccessibilityService : AccessibilityService() {

    companion object {
        private const val DISNEY_PACKAGE = "com.disney.disneyplus"
        private const val MIN_CHARS = 2
        private const val INTERVALO_MS = 300L
        private const val MAX_AUSENCIAS = 10
    }

    private val handler = Handler(Looper.getMainLooper())
    private var tts: TtsManager? = null
    private var overlayView: View? = null
    private var ultimaLegendaNorm: String = ""

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
        criarOverlay()
        iniciarPoll()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() == DISNEY_PACKAGE) iniciarPoll()
    }

    override fun onInterrupt() {
        tts?.parar()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        pararPoll()
        tts?.libertar()
        tts = null
        removerOverlay()
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

    // ---------------------------------------------------------------------
    // Leitura
    // ---------------------------------------------------------------------

    private fun anunciar(legenda: String) {
        val norm = normalizar(legenda)
        if (norm.isEmpty() || norm == ultimaLegendaNorm) return
        ultimaLegendaNorm = norm
        if (usarLeitorEcra) {
            anunciarPeloLeitorEcra(legenda)
        } else {
            garantirTts().falar(legenda)
        }
    }

    /** Faz o TalkBack ler o texto, através do overlay de acessibilidade. */
    private fun anunciarPeloLeitorEcra(texto: String) {
        val v = overlayView ?: return
        v.announceForAccessibility(texto)
    }

    private fun garantirTts(): TtsManager =
        tts ?: TtsManager(this).also { tts = it }

    private fun normalizar(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{Nd}]"), "")

    // ---------------------------------------------------------------------
    // Overlay (para announceForAccessibility)
    // ---------------------------------------------------------------------

    private fun criarOverlay() {
        if (overlayView != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val v = View(this)
        val lp = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        runCatching {
            wm.addView(v, lp)
            overlayView = v
        }
    }

    private fun removerOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        overlayView?.let { v -> runCatching { wm?.removeView(v) } }
        overlayView = null
    }

    // ---------------------------------------------------------------------
    // Deteção das legendas (árvore de acessibilidade)
    // ---------------------------------------------------------------------

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

    private fun compor(candidatos: List<Pair<Rect, String>>): String? {
        val alturaEcra = resources.displayMetrics.heightPixels
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

    private fun pareceLegenda(node: AccessibilityNodeInfo, texto: String): Boolean {
        if (node.isClickable || node.isEditable || node.isCheckable) return false
        if (texto.length < MIN_CHARS) return false
        return texto.any { it.isLetter() }
    }
}
