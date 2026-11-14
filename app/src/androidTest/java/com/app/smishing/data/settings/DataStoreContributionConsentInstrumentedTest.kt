package com.app.smishing.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.domain.sharing.ContributionRejection
import com.app.smishing.domain.sharing.ContributionResult
import com.app.smishing.domain.sharing.PrepareContribution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * #18: el consentimiento del aporte al corpus está desactivado por defecto, se puede activar y revocar, sobrevive a reabrir el almacén y
 * revocarlo detiene el aporte siguiente. Cada prueba usa su propio archivo temporal, así no toca la preferencia real de la app.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreContributionConsentInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var file: File
    private val scopes = mutableListOf<CoroutineScope>()

    @Before
    fun setUp() {
        file = File.createTempFile("consent-test", ".preferences_pb", context.cacheDir).also { it.delete() }
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
    fun withoutAChoiceTheContributionIsOff() {
        assertFalse(DataStoreContributionConsent(open()).isGranted())
    }

    @Test
    fun itCanBeGrantedAndRevoked() {
        val consent = DataStoreContributionConsent(open())
        consent.setGranted(true)
        assertTrue(consent.isGranted())
        consent.setGranted(false)
        assertFalse(consent.isGranted())
    }

    @Test
    fun theChoiceSurvivesReopeningTheStore() {
        val first = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        DataStoreContributionConsent(PreferenceDataStoreFactory.create(scope = first) { file }).setGranted(true)
        first.cancel() // cierra el primero antes de abrir otro sobre el mismo archivo

        assertTrue(DataStoreContributionConsent(open()).isGranted())
    }

    @Test
    fun revokingItStopsTheNextContributionWithTheRealStore() {
        val consent = DataStoreContributionConsent(open())
        val prepare = PrepareContribution(consent)
        val message = "Llame al 912345678 o entre a https://banco.example/x"

        assertEquals(ContributionResult.Rejected(ContributionRejection.NoConsent), prepare.prepare(message))
        consent.setGranted(true)
        assertEquals("Llame al <PHONE_NUMBER> o entre a <URL>", (prepare.prepare(message) as ContributionResult.Ready).text)
        consent.setGranted(false)
        assertEquals(ContributionResult.Rejected(ContributionRejection.NoConsent), prepare.prepare(message))
    }
}
