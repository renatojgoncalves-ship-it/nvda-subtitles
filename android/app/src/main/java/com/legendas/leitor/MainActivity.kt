package com.legendas.leitor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.legendas.leitor.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var ttsTeste: TtsManager? = null

    // (nome apresentado, tag BCP-47)
    private val idiomas = listOf(
        "Português (Portugal)" to "pt-PT",
        "Português (Brasil)" to "pt-BR",
        "English (US)" to "en-US",
        "Español (España)" to "es-ES",
        "Français (France)" to "fr-FR",
        "Deutsch" to "de-DE",
        "Italiano" to "it-IT",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarSpinnerIdioma()
        configurarControlos()

        binding.btnAcessibilidade.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnAbrirDisney.setOnClickListener { abrirDisney() }
        binding.btnTestar.setOnClickListener {
            ttsTeste?.aplicarPreferencias()
            ttsTeste?.falar(getString(R.string.frase_teste))
        }
    }

    override fun onResume() {
        super.onResume()
        atualizarEstadoServico()
    }

    override fun onDestroy() {
        ttsTeste?.libertar()
        ttsTeste = null
        super.onDestroy()
    }

    private fun configurarSpinnerIdioma() {
        val nomes = idiomas.map { it.first }
        binding.spinnerIdioma.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, nomes
        )
        val indiceAtual = idiomas.indexOfFirst { it.second == idioma }.coerceAtLeast(0)
        binding.spinnerIdioma.setSelection(indiceAtual)
        binding.spinnerIdioma.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long
                ) {
                    this@MainActivity.idioma = idiomas[position].second
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    private fun configurarControlos() {
        binding.switchLeitura.isChecked = leituraAtiva
        binding.switchLeitura.setOnCheckedChangeListener { _, v -> leituraAtiva = v }

        binding.switchInterromper.isChecked = interromper
        binding.switchInterromper.setOnCheckedChangeListener { _, v -> interromper = v }

        binding.sliderVelocidade.value = velocidade.coerceIn(0.5f, 2.5f)
        binding.sliderVelocidade.addOnChangeListener { _, value, _ -> velocidade = value }

        binding.sliderTom.value = tom.coerceIn(0.5f, 2.0f)
        binding.sliderTom.addOnChangeListener { _, value, _ -> tom = value }

        binding.sliderVolume.value = volume.coerceIn(0.1f, 1.0f)
        binding.sliderVolume.addOnChangeListener { _, value, _ -> volume = value }

        binding.switchBaixarConteudo.isChecked = baixarConteudo
        binding.switchBaixarConteudo.setOnCheckedChangeListener { _, v -> baixarConteudo = v }

        binding.sliderZona.value = zonaLegendas.coerceIn(0.2f, 0.8f)
        binding.sliderZona.addOnChangeListener { _, value, _ -> zonaLegendas = value }

        // TTS local apenas para o botão "Testar voz".
        ttsTeste = TtsManager(this)
    }

    private fun atualizarEstadoServico() {
        val ativo = servicoAtivo()
        binding.textoEstado.text =
            getString(if (ativo) R.string.estado_ativo else R.string.estado_inativo)
    }

    /** Verifica se o nosso serviço consta da lista de serviços de acessibilidade ativos. */
    private fun servicoAtivo(): Boolean {
        val esperado = ComponentName(this, SubtitleAccessibilityService::class.java)
        val ativos = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(ativos)
        for (item in splitter) {
            val comp = ComponentName.unflattenFromString(item)
            if (comp != null && comp == esperado) return true
        }
        return false
    }

    private fun abrirDisney() {
        val intent = packageManager.getLaunchIntentForPackage("com.disney.disneyplus")
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, R.string.disney_nao_instalado, Toast.LENGTH_LONG).show()
        }
    }
}
