package com.app.smishing.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.domain.mitigation.MitigationMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * El modo de mitigación guardado en un DataStore (#13). Cada prueba usa su propio archivo temporal, así no toca la preferencia real
 * de la app.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreMitigationSettingsInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var file: File
    private val scopes = mutableListOf<CoroutineScope>()

    @Before
    fun setUp() {
        file = File.createTempFile("settings-test", ".preferences_pb", context.cacheDir).also { it.delete() }
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        file.delete()
    }

    /** Un DataStore sobre el archivo de la prueba. Abrir otro sobre el mismo archivo mientras el primero vive no está permitido. */
    private fun open(): DataStore<Preferences> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        return PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    @Test
    fun withoutAChoiceTheModeIsAsk() {
        assertEquals(MitigationMode.Ask, DataStoreMitigationSettings(open()).mode())
    }

    @Test
    fun theChosenModeIsReadBack() {
        val settings = DataStoreMitigationSettings(open())
        for (mode in MitigationMode.entries) {
            settings.setMode(mode)
            assertEquals(mode, settings.mode())
        }
    }

    @Test
    fun theChoiceSurvivesReopeningTheStore() {
        val first = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = first) { file }
        DataStoreMitigationSettings(store).setMode(MitigationMode.Disabled)
        first.cancel() // cierra el primero antes de abrir otro sobre el mismo archivo

        assertEquals(MitigationMode.Disabled, DataStoreMitigationSettings(open()).mode())
    }

    @Test
    fun anUnknownSavedValueFallsBackToAsk() {
        val store = open()
        runBlocking { store.edit { it[stringPreferencesKey("mitigation_mode")] = "valor-que-ya-no-existe" } }
        assertEquals(MitigationMode.Ask, DataStoreMitigationSettings(store).mode())
    }

    @Test
    fun theSavedValueIsTheStableIdNotTheEnumName() {
        val store = open()
        DataStoreMitigationSettings(store).setMode(MitigationMode.Automatic)
        val raw = runBlocking { store.edit { } }[stringPreferencesKey("mitigation_mode")]
        assertEquals("automatic", raw)
    }

    @Test
    fun aListenerHearsAChangeButNotTheValueThatWasAlreadyThere() {
        val settings = DataStoreMitigationSettings(open())
        settings.setMode(MitigationMode.Ask)
        val heard = CountDownLatch(1)
        var calls = 0
        val subscription = settings.onChange { calls++; heard.countDown() }

        Thread.sleep(300) // un instante para que el listener lea el valor inicial
        assertEquals("El valor que ya había no es un cambio", 0, calls)

        settings.setMode(MitigationMode.Automatic)
        assertTrue("No avisó del cambio", heard.await(5, TimeUnit.SECONDS))
        subscription.close()
    }

    @Test
    fun aClosedListenerNoLongerHearsChanges() {
        val settings = DataStoreMitigationSettings(open())
        var calls = 0
        settings.onChange { calls++ }.close()
        Thread.sleep(200)
        settings.setMode(MitigationMode.Disabled)
        Thread.sleep(500)
        assertEquals(0, calls)
    }
}
