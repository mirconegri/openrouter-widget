package com.nothingwidget.openrouter

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nothingwidget.openrouter.data.OpenRouterApi
import com.nothingwidget.openrouter.data.Prefs
import com.nothingwidget.openrouter.databinding.ActivityMainBinding
import com.nothingwidget.openrouter.widget.WidgetUpdater
import com.nothingwidget.openrouter.work.RefreshWorker
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)

        b.buttonSaveUserKey.setOnClickListener { validateAndSaveUserKey() }
        b.buttonLogout.setOnClickListener { doLogout() }
        b.buttonSaveConfig.setOnClickListener { saveConfigAndRefresh() }
        b.radioAccount.setOnClickListener { setMode(Prefs.MODE_ACCOUNT) }
        b.radioKey.setOnClickListener { setMode(Prefs.MODE_KEY) }

        render()
    }

    private fun render() {
        val loggedIn = prefs.userApiKey.isNotBlank()
        b.groupLoggedOut.visibility = if (loggedIn) View.GONE else View.VISIBLE
        b.groupConfig.visibility = if (loggedIn) View.VISIBLE else View.GONE

        if (loggedIn) {
            b.inputUserKey.setText(prefs.userApiKey)
            b.inputThreshold.setText(prefs.threshold.toString())
            when (prefs.displayMode) {
                Prefs.MODE_KEY -> b.radioKey.isChecked = true
                else -> b.radioAccount.isChecked = true
            }
            updateThresholdHint()
        }
    }

    private fun validateAndSaveUserKey() {
        val key = b.inputUserKey.text?.toString()?.trim().orEmpty()
        if (key.isBlank()) { toast("Enter your API key"); return }
        b.status.text = "VERIFYING API KEY..."
        lifecycleScope.launch {
            try {
                val r = OpenRouterApi.fetchCredits(key)
                prefs.userApiKey = key
                prefs.cachedBalance = r.balance
                prefs.cachedUsage = r.usage
                prefs.cachedLabel = "ACCOUNT"
                prefs.lastUpdate = System.currentTimeMillis()
                prefs.lastError = null
                b.status.text = "OK · BALANCE $" + String.format(Locale.US, "%.2f", r.balance)
                WidgetUpdater.updateAll(this@MainActivity)
                RefreshWorker.schedule(this@MainActivity)
                render()
            } catch (t: Throwable) {
                b.status.text = "ERROR: ${t.message}"
            }
        }
    }

    private fun doLogout() {
        prefs.logout()
        b.inputUserKey.setText("")
        render()
        b.status.text = "LOGGED OUT"
        WidgetUpdater.updateAll(this)
    }

    private fun setMode(mode: String) {
        prefs.displayMode = mode
        updateThresholdHint()
    }

    /**
     * The threshold has opposite meaning depending on the active display mode:
     * - ACCOUNT: alert when the remaining balance DROPS below the threshold
     * - KEY:     alert when the cumulative usage RISES above the threshold
     */
    private fun updateThresholdHint() {
        b.hintThreshold.text = when (prefs.displayMode) {
            Prefs.MODE_KEY -> "Widget turns red when key usage rises above this value"
            else -> "Widget turns red when balance drops below this value"
        }
    }

    private fun saveConfigAndRefresh() {
        val t = b.inputThreshold.text?.toString()?.toFloatOrNull() ?: 2.0f
        prefs.threshold = t
        b.status.text = "SAVED · SYNCING..."
        WidgetUpdater.updateAll(this)
        RefreshWorker.runNow(this)
        toast("Saved")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}