package com.nothingwidget.openrouter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import com.nothingwidget.openrouter.data.ApiKeyInfo
import com.nothingwidget.openrouter.data.OAuthHelper
import com.nothingwidget.openrouter.data.OpenRouterApi
import com.nothingwidget.openrouter.data.Prefs
import com.nothingwidget.openrouter.data.Workspace
import com.nothingwidget.openrouter.databinding.ActivityMainBinding
import com.nothingwidget.openrouter.widget.WidgetUpdater
import com.nothingwidget.openrouter.work.RefreshWorker
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs

    private var workspaces: List<Workspace> = emptyList()
    private var keys: List<ApiKeyInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)

        b.buttonLogin.setOnClickListener { startOAuth() }
        b.buttonSaveUserKey.setOnClickListener { validateAndSaveUserKey() }
        b.buttonLogout.setOnClickListener { doLogout() }
        b.buttonSaveMgmtKey.setOnClickListener { validateAndSaveManagementKey() }
        b.buttonSaveConfig.setOnClickListener { saveConfigAndRefresh() }
        b.radioAccount.setOnClickListener { setMode(Prefs.MODE_ACCOUNT) }
        b.radioWorkspace.setOnClickListener { setMode(Prefs.MODE_WORKSPACE) }
        b.radioKey.setOnClickListener { setMode(Prefs.MODE_KEY) }

        b.spinnerWorkspace.setOnItemClickListener { _, _, pos, _ ->
            if (pos in workspaces.indices) {
                prefs.selectedWorkspaceId = workspaces[pos].id
                prefs.selectedWorkspaceName = workspaces[pos].name
                loadKeysForSelectedWorkspace()
                updateSaveEnabled()
            }
        }
        b.spinnerKey.setOnItemClickListener { _, _, pos, _ ->
            if (pos in keys.indices) {
                prefs.selectedKeyHash = keys[pos].hash
                prefs.selectedKeyName = keys[pos].name
                updateSaveEnabled()
            }
        }

        render()
        handleOAuthCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthCallback(intent)
    }

    private fun render() {
        val loggedIn = prefs.userApiKey.isNotBlank()
        b.groupLoggedOut.visibility = if (loggedIn) View.GONE else View.VISIBLE
        b.groupConfig.visibility = if (loggedIn) View.VISIBLE else View.GONE

        if (loggedIn) {
            b.inputMgmtKey.setText(prefs.managementKey)
            b.inputThreshold.setText(prefs.threshold.toString())
            when (prefs.displayMode) {
                Prefs.MODE_WORKSPACE -> b.radioWorkspace.isChecked = true
                Prefs.MODE_KEY -> b.radioKey.isChecked = true
                else -> b.radioAccount.isChecked = true
            }
            applyModeVisibility()
            if (prefs.managementKey.isNotBlank()) loadWorkspaces()
        }
    }

    private fun startOAuth() {
        val verifier = OAuthHelper.generateCodeVerifier()
        val challenge = OAuthHelper.codeChallenge(verifier)
        prefs.oauthCodeVerifier = verifier
        val url = OAuthHelper.buildAuthUrl(challenge)
        try {
            CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url))
        } catch (t: Throwable) {
            toast("Browser non disponibile")
        }
    }

    private fun validateAndSaveUserKey() {
        val key = b.inputUserKey.text?.toString()?.trim().orEmpty()
        if (key.isBlank()) { toast("Inserisci la User API Key"); return }
        b.status.text = "VERIFICA USER KEY..."
        lifecycleScope.launch {
            try {
                val r = OpenRouterApi.fetchCredits(key)
                prefs.userApiKey = key
                prefs.cachedBalance = r.balance
                prefs.cachedUsage = r.usage
                prefs.cachedLabel = "ACCOUNT"
                prefs.lastUpdate = System.currentTimeMillis()
                prefs.lastError = null
                b.status.text = "OK · SALDO $" + String.format(Locale.US, "%.2f", r.balance)
                WidgetUpdater.updateAll(this@MainActivity)
                render()
            } catch (t: Throwable) {
                b.status.text = "ERRORE: ${t.message}"
            }
        }
    }

    private fun handleOAuthCallback(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != OAuthHelper.CALLBACK_SCHEME || data.host != OAuthHelper.CALLBACK_HOST) return

        val err = data.getQueryParameter("error")
        if (err != null) { toast("OAuth: $err"); return }

        val code = data.getQueryParameter("code") ?: return
        val verifier = prefs.oauthCodeVerifier
        if (verifier.isNullOrBlank()) { toast("Verifier mancante"); return }

        b.status.text = "SCAMBIO TOKEN..."
        lifecycleScope.launch {
            try {
                val res = OAuthHelper.exchangeCode(code, verifier)
                prefs.userApiKey = res.apiKey
                prefs.oauthCodeVerifier = null
                b.status.text = "LOGIN OK"
                render()
                WidgetUpdater.updateAll(this@MainActivity)
            } catch (t: Throwable) {
                b.status.text = "ERRORE: ${t.message}"
            }
        }
    }

    private fun doLogout() {
        prefs.logout()
        workspaces = emptyList(); keys = emptyList()
        b.inputMgmtKey.setText("")
        b.inputUserKey.setText("")
        b.spinnerWorkspace.setAdapter(null)
        b.spinnerKey.setAdapter(null)
        render()
        b.status.text = "DISCONNESSO"
    }

    private fun validateAndSaveManagementKey() {
        val key = b.inputMgmtKey.text?.toString()?.trim().orEmpty()
        if (key.isBlank()) { toast("Inserisci la Management Key"); return }
        b.status.text = "VERIFICA MGMT KEY..."
        lifecycleScope.launch {
            try {
                val ws = OpenRouterApi.fetchWorkspaces(key)
                prefs.managementKey = key
                workspaces = ws
                b.status.text = "OK · ${ws.size} WORKSPACE"
                populateWorkspaceSpinner()
            } catch (t: Throwable) {
                b.status.text = "MGMT KEY ERRORE: ${t.message}"
            }
        }
    }

    private fun loadWorkspaces() {
        lifecycleScope.launch {
            try {
                workspaces = OpenRouterApi.fetchWorkspaces(prefs.managementKey)
                populateWorkspaceSpinner()
            } catch (_: Throwable) { }
        }
    }

    private fun populateWorkspaceSpinner() {
        val names = workspaces.map { it.name.ifBlank { it.id } }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
        b.spinnerWorkspace.setAdapter(adapter)
        b.spinnerWorkspace.threshold = 0
        val idx = workspaces.indexOfFirst { it.id == prefs.selectedWorkspaceId }
        if (idx >= 0) b.spinnerWorkspace.setText(names[idx], false)
        if (prefs.selectedWorkspaceId != null) loadKeysForSelectedWorkspace()
    }

    private fun loadKeysForSelectedWorkspace() {
        val wsId = prefs.selectedWorkspaceId ?: return
        lifecycleScope.launch {
            try {
                keys = OpenRouterApi.fetchWorkspaceKeys(prefs.managementKey, wsId)
                val names = keys.map { it.name }
                val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, names)
                b.spinnerKey.setAdapter(adapter)
                b.spinnerKey.threshold = 0
                val idx = keys.indexOfFirst { it.hash == prefs.selectedKeyHash }
                if (idx >= 0) b.spinnerKey.setText(names[idx], false)
            } catch (t: Throwable) {
                toast("Keys: ${t.message}")
            }
        }
    }

    private fun setMode(mode: String) {
        prefs.displayMode = mode
        applyModeVisibility()
        updateSaveEnabled()
    }

    private fun applyModeVisibility() {
        when (prefs.displayMode) {
            Prefs.MODE_WORKSPACE -> {
                b.groupWorkspaceSel.visibility = View.VISIBLE
                b.groupKeySel.visibility = View.GONE
            }
            Prefs.MODE_KEY -> {
                b.groupWorkspaceSel.visibility = View.VISIBLE
                b.groupKeySel.visibility = View.VISIBLE
            }
            else -> {
                b.groupWorkspaceSel.visibility = View.GONE
                b.groupKeySel.visibility = View.GONE
            }
        }
    }

    private fun updateSaveEnabled() {
        val ok = when (prefs.displayMode) {
            Prefs.MODE_WORKSPACE -> prefs.selectedWorkspaceId != null
            Prefs.MODE_KEY -> prefs.selectedWorkspaceId != null && prefs.selectedKeyHash != null
            else -> true
        }
        b.buttonSaveConfig.isEnabled = ok
    }

    private fun saveConfigAndRefresh() {
        val t = b.inputThreshold.text?.toString()?.toFloatOrNull() ?: 2.0f
        prefs.threshold = t
        b.status.text = "SALVATO · SINCRONIZZO..."
        WidgetUpdater.updateAll(this)
        RefreshWorker.runNow(this)
        toast("Salvato")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}