package com.app.smishing.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.domain.tutorial.TutorialState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * #88: el recorrido guiado está pendiente por defecto, se guarda como visto u omitido, se puede volver a pendiente y la decisión sobrevive
 * a reabrir el almacén. Cada prueba usa su propio archivo temporal, así no toca la preferencia real de la app.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreTutorialProgressInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var file: File
    private val scopes = mutableListOf<CoroutineScope>()

    @Before
    fun setUp() {
        file = File.createTempFile("tutorial-test", ".preferences_pb", context.cacheDir).also { it.delete() }
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        file.delete()
    }

    private fun open(): DataStore<Preferences> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        return PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    @Test
    fun withoutAChoiceItIsPending() {
        assertEquals(TutorialState.Pending, DataStoreTutorialProgress(open()).state())
    }

    @Test
    fun itCanBeDoneSkippedAndPendingAgain() {
        val progress = DataStoreTutorialProgress(open())
        for (state in listOf(TutorialState.Done, TutorialState.Skipped, TutorialState.Pending)) {
            progress.setState(state)
            assertEquals(state, progress.state())
        }
    }

    @Test
    fun theChoiceSurvivesReopeningTheStore() {
        val first = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        DataStoreTutorialProgress(PreferenceDataStoreFactory.create(scope = first) { file }).setState(TutorialState.Skipped)
        first.cancel() // cierra el primero antes de abrir otro sobre el mismo archivo
        assertEquals(TutorialState.Skipped, DataStoreTutorialProgress(open()).state())
    }
}
