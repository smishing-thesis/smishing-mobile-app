package com.app.smishing.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.app.smishing.domain.sharing.ContributionConsent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * El consentimiento del aporte al corpus (#18) en el mismo DataStore de preferencias que el modo. Sin nada guardado es `false`: el aporte
 * está desactivado por defecto. Leer y escribir bloquean el hilo que llama: no usar desde el hilo principal.
 */
class DataStoreContributionConsent(private val store: DataStore<Preferences>) : ContributionConsent {

    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    override fun isGranted(): Boolean = runBlocking { store.data.first()[KEY] ?: false }

    override fun setGranted(granted: Boolean) {
        runBlocking { store.edit { it[KEY] = granted } }
    }

    private companion object {
        val KEY = booleanPreferencesKey("contribution_consent")
    }
}
