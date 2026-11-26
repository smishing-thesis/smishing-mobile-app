package com.app.smishing.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.app.smishing.domain.tutorial.TutorialProgress
import com.app.smishing.domain.tutorial.TutorialState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Qué decidió la persona del recorrido guiado (#88), en el mismo DataStore de preferencias que el modo y el onboarding. Se guarda el `id`
 * estable del estado, no el nombre del enumerado. Leer y escribir bloquean el hilo que llama: no usar desde el hilo principal.
 */
class DataStoreTutorialProgress(private val store: DataStore<Preferences>) : TutorialProgress {

    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    override fun state(): TutorialState = runBlocking { TutorialState.fromId(store.data.first()[KEY_TUTORIAL_STATE]) }

    override fun setState(state: TutorialState) {
        runBlocking { store.edit { it[KEY_TUTORIAL_STATE] = state.id } }
    }

    private companion object {
        val KEY_TUTORIAL_STATE = stringPreferencesKey("tutorial_state")
    }
}
