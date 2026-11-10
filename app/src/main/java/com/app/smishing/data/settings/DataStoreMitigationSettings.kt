package com.app.smishing.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.app.smishing.domain.mitigation.MitigationMode
import com.app.smishing.domain.mitigation.MitigationSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Las preferencias de mitigación en un DataStore de Jetpack (#13), como define la arquitectura. Se guarda el `id` del modo, no su
 * nombre. Si no hay nada guardado, o el valor no se reconoce, el modo es [MitigationMode.DEFAULT].
 *
 * Leer y escribir bloquean el hilo que llama: no usar desde el hilo principal (la pantalla de Ajustes lo hace en `Dispatchers.IO`).
 */
class DataStoreMitigationSettings(private val store: DataStore<Preferences>) : MitigationSettings {

    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    override fun mode(): MitigationMode = MitigationMode.fromId(runBlocking { store.data.first()[KEY_MODE] })

    override fun setMode(mode: MitigationMode) {
        runBlocking { store.edit { it[KEY_MODE] = mode.id } }
    }

    override fun onChange(listener: () -> Unit): AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            // `drop(1)`: el primer valor es el que ya había, no un cambio.
            store.data.map { it[KEY_MODE] }.distinctUntilChanged().drop(1).collect { listener() }
        }
        return AutoCloseable { scope.cancel() }
    }

    private companion object {
        val KEY_MODE = stringPreferencesKey("mitigation_mode")
    }
}
