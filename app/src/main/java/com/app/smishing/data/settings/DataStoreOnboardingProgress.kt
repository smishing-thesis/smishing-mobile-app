package com.app.smishing.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.app.smishing.domain.settings.OnboardingProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Qué pasos de la bienvenida ya se mostraron, en el mismo DataStore de preferencias que el modo (#17). Leer y escribir bloquean el
 * hilo que llama: no usar desde el hilo principal.
 */
class DataStoreOnboardingProgress(private val store: DataStore<Preferences>) : OnboardingProgress {

    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    override fun welcomeSeen(): Boolean = runBlocking { store.data.first()[KEY_WELCOME_SEEN] ?: false }

    override fun setWelcomeSeen(seen: Boolean) {
        runBlocking { store.edit { it[KEY_WELCOME_SEEN] = seen } }
    }

    override fun contactsPromptSeen(): Boolean = runBlocking { store.data.first()[KEY_CONTACTS_PROMPT_SEEN] ?: false }

    override fun setContactsPromptSeen(seen: Boolean) {
        runBlocking { store.edit { it[KEY_CONTACTS_PROMPT_SEEN] = seen } }
    }

    private companion object {
        val KEY_WELCOME_SEEN = booleanPreferencesKey("welcome_seen")
        val KEY_CONTACTS_PROMPT_SEEN = booleanPreferencesKey("contacts_prompt_seen")
    }
}
