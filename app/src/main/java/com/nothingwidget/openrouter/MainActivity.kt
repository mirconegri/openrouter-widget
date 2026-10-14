package com.nothingwidget.openrouter

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nothingwidget.openrouter.data.OpenRouterApi
import com.nothingwidget.openrouter.data.Prefs
import com.nothingwidget.openrouter.databinding.ActivityMainBinding
import com.nothingwidget.openrouter.widget.WidgetUpdater
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        binding.inputApiKey.setText(prefs.apiKey)
        binding.inputThreshold.setText(prefs.threshold.toString())

        // Stato iniziale
        val cached = prefs.cachedBalance
        if (cached >= 0f) {
            binding.status.text = "ULTIMO SALDO: $" + String.format(Locale.US, "%.2f", cached)
        } else {
            binding.status.text = "NESSUN DATO IN CACHE"
        }

        binding.buttonSave.setOnClickListener { verifyAndSave() }
    }

    private fun verifyAndSave() {
        val key = binding.inputApiKey.text?.toString()?.trim().orEmpty()
        val threshold = binding.inputThreshold.text?.toString()?.toFloatOrNull() ?: 2.0f

        if (key.isBlank()) {
            toast("Inserisci una API Key valida")
            return
        }

        binding.buttonSave.isEnabled = false
        binding.status.text = "VERIFICA IN CORSO..."
        binding.status.setTextColor(0xFF8E8E93.toInt())

        lifecycleScope.launch {
            try {
                val r = OpenRouterApi.fetchCredits(key)

                prefs.apiKey = key
                prefs.threshold = threshold
                prefs.cachedBalance = r.balance
                prefs.cachedUsage = r.usage
                prefs.lastUpdate = System.currentTimeMillis()
                prefs.lastError = null

                WidgetUpdater.updateAll(this@MainActivity)

                binding.status.text =
                    "OK · SALDO $" + String.format(Locale.US, "%.2f", r.balance)
                binding.status.setTextColor(0xFF4CD964.toInt())
                toast("Salvato e widget aggiornato")
            } catch (t: Throwable) {
                prefs.lastError = t.message
                binding.status.text = "ERRORE: " + (t.message ?: "sconosciuto")
                binding.status.setTextColor(0xFFD71921.toInt())
            } finally {
                binding.buttonSave.isEnabled = true
            }
        }
    }

    private fun toast(s: String) =
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}