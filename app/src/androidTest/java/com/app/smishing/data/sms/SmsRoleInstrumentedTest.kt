package com.app.smishing.data.sms

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.app.smishing.R
import com.app.smishing.DeviceReport
import com.app.smishing.PlainLanguage
import com.app.smishing.Shell
import com.app.smishing.data.analysis.AnalysisPipeline
import com.app.smishing.data.messaging.TelephonyConversationRepository
import com.app.smishing.data.settings.DataStoreMitigationSettings
import com.app.smishing.data.settings.DataStoreOnboardingProgress
import com.app.smishing.domain.mitigation.MitigationDecision
import com.app.smishing.domain.mitigation.MitigationMode
import com.app.smishing.data.classification.OnDeviceClassifier
import com.app.smishing.data.history.HistoryDatabase
import com.app.smishing.data.history.RoomAnalysisHistory
import com.app.smishing.domain.mitigation.latest
import com.app.smishing.domain.mitigation.ActionType
import com.app.smishing.domain.mitigation.ActionStatus
import com.app.smishing.data.history.RoomMitigationLog
import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.classification.GatedMessageClassifier
import com.app.smishing.domain.messaging.formatConversationDate
import com.app.smishing.domain.risk.RiskLevel
import com.app.smishing.domain.signals.SignalType
import com.app.smishing.domain.sms.AnalysisStatus
import com.app.smishing.domain.sms.RoleAndPermissionsGate
import com.app.smishing.domain.sms.SmsPermissionState
import com.app.smishing.domain.sms.SmsRoleState
import com.app.smishing.domain.sms.SuspensionReason
import com.app.smishing.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.json.JSONObject
import java.time.ZoneId
import java.util.Locale

/**
 * #2 a #5: la app es candidata a gestor de SMS, los permisos se piden después del rol y el análisis solo se activa con
 * el rol y los permisos. El rol se concede con un comando de shell, igual que lo haría la persona en los ajustes.
 *
 * Ninguna prueba puede quitar el rol ni un permiso: Android mata el proceso de la app al revocarlos, y estas pruebas
 * corren dentro de ese proceso (se comprobó: "Instrumentation run failed due to Process crashed"). Por eso el flujo solo
 * avanza, de "sin rol" a "con rol", y termina con el rol concedido; Gradle desinstala la app al acabar. La revocación se
 * prueba en dos fases, quitando el permiso desde fuera entre una ejecución y otra (ver docs/permisos-sms.md).
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING) // el flujo del rol va antes que la recepción, que necesita el rol
class SmsRoleInstrumentedTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @get:Rule
    val testName = org.junit.rules.TestName()

    /**
     * Desde Android 13 el permiso de notificaciones no viene concedido. Las pruebas que miran notificaciones parten de concedido;
     * la que prueba la negativa (`roleThenNotificationsFollowTheAndroid13Permission`) parte de la instalación limpia.
     */
    @org.junit.Before
    fun allowNotificationsFromAndroid13() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && testName.methodName != NOTIFICATION_PERMISSION_TEST && testName.methodName != "roleFirstThenPermissionsThenAnalysis") {
            Shell.run("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        }
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val roles = AndroidSmsRoleRepository(context)
    private val permissions = AndroidSmsPermissionRepository(context)

    @Test
    fun theSystemOffersTheRoleRequest() {
        assertNotNull(roles.requestIntent())
    }

    @Test
    fun roleFirstThenPermissionsThenAnalysis() {
        assumeTrue(
            "La app ya tenía el rol: desinstálala para repetir esta prueba (Gradle lo hace al terminar).",
            roles.state() == SmsRoleState.NotHeld,
        )
        val gate = RoleAndPermissionsGate(roles, permissions)
        val gated = { classifier: OnDeviceClassifier -> GatedMessageClassifier(classifier, gate) }
        val message = "Tu codigo de verificacion es 458920. No lo compartas con nadie."

        // 1. Sin rol ni permisos, el análisis está suspendido y el modelo no se usa.
        assertEquals(SmsPermissionState.NotGranted, permissions.state())
        assertEquals(
            AnalysisStatus.Suspended(setOf(SuspensionReason.RoleNotHeld, SuspensionReason.PermissionsNotGranted)),
            gate.status(),
        )
        OnDeviceClassifier.create(context).use { classifier ->
            assertEquals(
                ClassificationResult.NotClassified(ClassificationResult.NotClassified.Reason.ANALYSIS_SUSPENDED),
                gated(classifier).classify(message),
            )
        }

        // La bienvenida (paso 1) sale hasta que se termina el onboarding: se parte de que aún no se terminó.
        DataStoreOnboardingProgress(context).setOnboardingDone(false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // 1b. Lo primero es la bienvenida: no pide nada ni menciona el rol.
            compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.welcome_title)) > 0 }
            // El paso 1 va sin puntos ni "PASO 1 DE 2".
            assertEquals(0, count(context.getString(R.string.default_sms_explanation_replace)))
            compose.onNodeWithText(context.getString(R.string.welcome_next)).performClick()
            // Pasar del paso 1 no termina el onboarding: si se cerrara aquí, la bienvenida volvería a salir.
            assertEquals(false, DataStoreOnboardingProgress(context).onboardingDone())

            // 2. Sin rol, la app pide primero el rol: todavía no menciona los permisos.
            compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.default_sms_explanation_replace)) > 0 }
            compose.onNodeWithText(context.getString(R.string.default_sms_explanation_replace)).assertIsDisplayed()
            assertEquals(0, count(context.getString(R.string.sms_permission_explanation_read)))

            // 3. La persona acepta el rol. Android concede con él los permisos de SMS.
            grantRole()
            assertEquals(SmsRoleState.Held, roles.state())
            assertEquals("Android debía conceder los permisos con el rol", SmsPermissionState.Granted, permissions.state())
            assertEquals(AnalysisStatus.Active, gate.status())

            // 4. Al volver a la app, ya tiene el rol y los permisos.
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            // Con el rol y los permisos, la app deja el onboarding y muestra Inicio con la protección activa (#17).
            compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.home_active_title)) > 0 }
            compose.onNodeWithText(context.getString(R.string.home_active_title)).assertIsDisplayed()
            assertEquals(0, count(context.getString(R.string.protection_off_title)))
            // Aceptar el rol termina el onboarding; se guarda en segundo plano.
            waitUntil { DataStoreOnboardingProgress(context).onboardingDone() }
            // Y desde la barra inferior se llega a los mensajes (#5).
            openMessagesTab()
            compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.messages_title)) > 1 }
        }

        // 5. Con el análisis activo, el clasificador con compuerta analiza el mensaje.
        OnDeviceClassifier.create(context).use { classifier ->
            val result = gated(classifier).classify(message)
            assertTrue("Con rol y permisos debía analizar: $result", result is ClassificationResult.Classified)
        }
    }

    /**
     * #4: con el rol, un SMS entrante queda una sola vez en la conversación de su remitente. Los mensajes se entregan
     * al receptor como lo hace el sistema (un `SMS_DELIVER` con PDUs) y se leen del buzón del sistema. Son sintéticos,
     * con remitentes ficticios, y se borran al terminar.
     */
    @Test
    fun thenReceivedSmsLandsOnceInItsConversation() {
        if (roles.state() != SmsRoleState.Held) grantRole()
        assertEquals(SmsRoleState.Held, roles.state())
        deleteSyntheticMessages()
        try {
            val first = "Tu codigo de verificacion es 458920. No lo compartas con nadie."
            val second = "Segundo mensaje del mismo remitente."
            val other = "Mensaje de otro remitente."
            val longPartOne = "Esta es la primera parte de un mensaje largo, "
            val longPartTwo = "y esta es la segunda parte."

            deliver(SENDER_A, first)
            deliver(SENDER_A, first) // el mismo mensaje entregado otra vez: no debe duplicarse
            deliver(SENDER_B, other)
            deliver(SENDER_A, second, secondsAfterSentAt = 30)
            deliver(SENDER_C, longPartOne, longPartTwo)

            // El receptor trabaja en un solo hilo y en orden: cuando el último mensaje está, los anteriores ya se procesaron.
            waitUntil { inbox(SENDER_C).size == 1 }

            val fromA = inbox(SENDER_A)
            assertEquals("El mensaje entregado dos veces debe aparecer una vez, más el segundo", 2, fromA.size)
            assertEquals(setOf(first, second), fromA.map { it.body }.toSet())
            assertEquals("Los dos mensajes de A van a la misma conversación", 1, fromA.map { it.threadId }.distinct().size)
            val fromB = inbox(SENDER_B).single()
            assertTrue("B va a otra conversación", fromB.threadId != fromA.first().threadId)
            assertEquals(other, fromB.body)
            assertEquals("Un mensaje largo en partes se guarda como uno solo", longPartOne + longPartTwo, inbox(SENDER_C).single().body)
            assertEquals("La hora de envío es la del operador", SmsPdu.SENT_AT_UTC_MILLIS, fromA.first { it.body == first }.sentAt)
            assertTrue("Debe quedar sin leer", fromA.all { !it.read })
        } finally {
            deleteSyntheticMessages()
        }
    }

    /**
     * #5: la lista muestra el remitente, la fecha y el último mensaje; la conversación muestra los recibidos y los enviados
     * en orden; y un SMS que llega con la lista abierta aparece sin reabrir la app. Datos sintéticos, que se borran al terminar.
     */
    @Test
    fun thenTheConversationsShowReceivedAndSentMessages() {
        if (roles.state() != SmsRoleState.Held) grantRole()
        assertEquals(SmsRoleState.Held, roles.state())
        deleteSyntheticMessages()
        try {
            val sentFirst = "Hola, ¿recibiste mi mensaje?"
            val received = "Si, ya lo recibi. Gracias."
            val sentLast = "Perfecto, nos vemos luego."
            val sender = "+$SENDER_D"

            insertSent(SENDER_D, sentFirst, System.currentTimeMillis() - 180_000)
            deliver(SENDER_D, received)
            waitUntil { inbox(SENDER_D).size == 1 }
            val lastAt = System.currentTimeMillis()
            insertSent(SENDER_D, sentLast, lastAt)

            ActivityScenario.launch(MainActivity::class.java).use {
                openMessagesTab()
                // La lista: remitente, fecha y el último mensaje de la conversación.
                compose.waitUntil(TIMEOUT_MS) { count(sender) > 0 }
                compose.onNodeWithText(sender).assertIsDisplayed()
                compose.onNodeWithText(sentLast).assertIsDisplayed()
                val date = formatConversationDate(
                    lastAt, System.currentTimeMillis(), ZoneId.systemDefault(), context.getString(R.string.messages_yesterday), Locale.forLanguageTag("es"),
                )
                assertTrue("La lista debía mostrar la fecha $date", count(date) > 0)

                // La conversación: recibido y enviado, del más antiguo al más reciente.
                compose.onNodeWithText(sender).performClick()
                compose.waitUntil(TIMEOUT_MS) { count(received) > 0 }
                compose.onNodeWithText(context.getString(R.string.conversation_subtitle)).assertIsDisplayed()
                fun bubble(text: String, description: Int) = compose.onNode(
                    hasText(text).and(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(description))),
                ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val first = bubble(sentFirst, R.string.message_sent)
                val middle = bubble(received, R.string.message_received)
                val last = bubble(sentLast, R.string.message_sent)
                assertTrue("Del más antiguo al más reciente", first.top < middle.top && middle.top < last.top)

                // Volver a la lista, y recibir un SMS con la lista abierta.
                compose.onNodeWithContentDescription(context.getString(R.string.conversation_back)).performClick()
                compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.messages_title)) > 0 && count(sender) > 0 }
                val arriving = "Mensaje nuevo con la lista abierta."
                deliver(SENDER_E, arriving)
                // Con muchas conversaciones en el teléfono la lista queda desplazada y la nueva, que va arriba, puede quedar fuera de
                // pantalla (como en cualquier lista): se vuelve al principio para verla.
                compose.waitUntil(TIMEOUT_MS) {
                    compose.onNode(androidx.compose.ui.test.hasScrollAction()).performScrollToIndex(0)
                    count(arriving) > 0
                }
                compose.onNodeWithText("+$SENDER_E").assertIsDisplayed()
            }
        } finally {
            deleteSyntheticMessages()
        }
    }

    /**
     * #5: con un buzón grande, la lista sigue teniendo una sola conversación por remitente, con su último mensaje. Los
     * tiempos se registran (no se exigen): dependen del dispositivo.
     */
    @Test
    fun thenALargeInboxStillListsOneConversationPerSender() {
        if (roles.state() != SmsRoleState.Held) grantRole()
        assertEquals(SmsRoleState.Held, roles.state())
        deleteBulkMessages()
        try {
            val senders = 150
            val perSender = 20
            val base = System.currentTimeMillis() - 10_000_000L
            val rows = Array(senders * perSender) { i ->
                ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, bulkAddress(i % senders))
                    put(Telephony.Sms.BODY, "Mensaje sintetico numero $i")
                    put(Telephony.Sms.DATE, base + i * 1000L)
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                }
            }
            context.contentResolver.bulkInsert(Telephony.Sms.Inbox.CONTENT_URI, rows)

            val repository = TelephonyConversationRepository(context)
            val listStart = System.nanoTime()
            val mine = repository.conversations().filter { it.address.startsWith(BULK_PREFIX) }
            val listMillis = (System.nanoTime() - listStart) / 1_000_000

            assertEquals("Una conversación por remitente", senders, mine.size)
            val indexOf = (0 until senders).associateBy { bulkAddress(it) }
            for (conversation in mine) {
                val sender = checkNotNull(indexOf[conversation.address])
                assertEquals("Mensaje sintetico numero ${(perSender - 1) * senders + sender}", conversation.snippet)
            }
            assertEquals("Más reciente primero", mine.map { it.lastMessageAtMillis }.sortedDescending(), mine.map { it.lastMessageAtMillis })

            val openStart = System.nanoTime()
            val thread = repository.messages(mine.first().threadId)
            val openMillis = (System.nanoTime() - openStart) / 1_000_000
            assertEquals(perSender, thread.size)
            assertEquals("Del más antiguo al más reciente", thread.map { it.atMillis }.sorted(), thread.map { it.atMillis })

            DeviceReport.write(
                context,
                "conversations-large-inbox.json",
                JSONObject()
                    .put("device", DeviceReport.deviceInfo())
                    .put("syntheticMessages", rows.size)
                    .put("conversations", senders)
                    .put("listConversationsMillis", listMillis)
                    .put("openOneConversationMillis", openMillis),
                "Conversations",
            )
        } finally {
            deleteBulkMessages()
        }
    }

    /**
     * #6: se redacta un mensaje con la interfaz (Escribir, número, texto, Enviar), el mensaje aparece en su conversación y el
     * sistema lo marca como enviado cuando la radio lo acepta. El destino es el propio emulador (5554), que lo entrega.
     * Datos sintéticos, que se borran al terminar.
     */
    @Test
    fun thenAMessageIsSentToTheChosenRecipientAndAppearsInItsConversation() {
        assumeTrue("El emulador entrega a sí mismo lo enviado a su número solo en el primero (5554); en $emulatorNumber no se probó", emulatorNumber == EMULATOR_NUMBER)
        if (roles.state() != SmsRoleState.Held) grantRole()
        assertEquals(SmsRoleState.Held, roles.state())
        assertEquals("Con el rol, Android debía conceder también el permiso de enviar", true, hasSendPermission())
        deleteEmulatorMessages()
        try {
            val text = ECHO_TEXT
            ActivityScenario.launch(MainActivity::class.java).use {
                openMessagesTab()
                compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.compose_action)) > 0 }
                compose.onNodeWithText(context.getString(R.string.compose_action)).performClick()

                compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.new_message_title)) > 0 }
                compose.onNodeWithText(context.getString(R.string.new_message_recipient_hint)).performTextInput(emulatorNumber)
                compose.onNodeWithText(context.getString(R.string.new_message_body_hint)).performTextInput(text)
                compose.onNodeWithText(context.getString(R.string.new_message_send)).performClick()

                // Aparece en su conversación, a la derecha, con el número elegido en la barra.
                compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.conversation_subtitle)) > 0 && count(text) > 0 }
                compose.onNodeWithText(emulatorNumber).assertIsDisplayed()
                compose.onNode(
                    hasText(text).and(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.message_sent))),
                ).assertIsDisplayed()
            }

            // En el buzón: una sola fila con ese texto, en una conversación, y la radio la acepta (enviado, no fallido).
            val type = { rowTypes(text).singleOrNull() }
            val deadline = System.currentTimeMillis() + TIMEOUT_MS
            while (type() != Telephony.Sms.MESSAGE_TYPE_SENT && System.currentTimeMillis() < deadline) Thread.sleep(100)
            assertEquals("Debía haber una sola fila saliente", 1, rowTypes(text).size)
            assertEquals(
                "El sistema debía marcarlo enviado (2); salió ${type()} (4 = saliente sin respuesta, 5 = fallido)",
                Telephony.Sms.MESSAGE_TYPE_SENT,
                type(),
            )
            // Está en una conversación como mensaje enviado y no fallido.
            val repository = TelephonyConversationRepository(context)
            val outgoing = repository.conversations().map { it.threadId }
                .flatMap { repository.messages(it) }
                .single { it.body == text && it.direction == com.app.smishing.domain.messaging.MessageDirection.Outgoing }
            assertEquals(false, outgoing.failed)

            // El destinatario lo recibió: el emulador entrega a sí mismo lo que se envía a su número, y llega una sola vez.
            // Solo se comprueba en Android 12 o anterior: en el emulador API 35 la copia no llegó en 10 s (la app ya había enviado bien),
            // y no se determinó si es del emulador o de otra cosa.
            if (android.os.Build.VERSION.SDK_INT < 33) {
                waitUntil { incomingCount(text) >= 1 }
                Thread.sleep(1_000)
                assertEquals("La copia recibida debía llegar una sola vez", 1, incomingCount(text))
            }
        } finally {
            deleteEmulatorMessages()
        }
    }

    /**
     * El número del emulador donde corre la prueba: los emuladores son 555-521-5554, 5556…, según su puerto. Se lee del sistema; si no
     * se puede (en Android 10 no sale), se usa el del primero, 5554. En Android 15 el emulador tiene el número de SIM 1-555-123-4567, que no
     * sigue al puerto: solo se acepta una terminación con forma de puerto (5554 a 5584); si no, se usa 5554.
     */
    private val emulatorNumber: String by lazy {
        val digits = Regex("'([^']*)'").findAll(Shell.run("service call iphonesubinfo 15"))
            .joinToString("") { it.groupValues[1].replace(".", "") }.filter { it.isDigit() }
        val last = digits.takeLast(4)
        if (last.toIntOrNull() in 5554..5584) last else EMULATOR_NUMBER
    }

    private fun hasSendPermission() =
        context.checkSelfPermission(android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Los tipos de las filas del buzón con este texto (el recibido por el eco del emulador tiene otro texto o tipo). */
    private fun rowTypes(body: String): List<Int> =
        context.contentResolver.query(
            Uri.parse("content://sms"),
            arrayOf(Telephony.Sms.TYPE),
            "${Telephony.Sms.BODY} = ? AND ${Telephony.Sms.TYPE} != ?",
            arrayOf(body, Telephony.Sms.MESSAGE_TYPE_INBOX.toString()),
            null,
        )!!.use { c -> generateSequence { if (c.moveToNext()) c.getInt(0) else null }.toList() }

    private fun incomingCount(body: String): Int =
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.BODY} = ?",
            arrayOf(body),
            null,
        )!!.use { it.count }

    private fun deleteEmulatorMessages() {
        context.contentResolver.delete(Uri.parse("content://sms"), "${Telephony.Sms.ADDRESS} LIKE ?", arrayOf("%$emulatorNumber%"))
        context.contentResolver.delete(Uri.parse("content://sms"), "${Telephony.Sms.BODY} = ?", arrayOf(ECHO_TEXT))
    }

    /**
     * #10 de punta a punta: un SMS riesgoso recibido genera una notificación que indica el riesgo sin mostrar el texto del
     * mensaje, y tocarla abre el detalle sin activar ningún enlace. Un mensaje que no es de riesgo alto no avisa. Datos
     * sintéticos, que se borran al terminar.
     */
    @Test
    fun thenARiskyMessageAlertsAndTheAlertOpensItsDetailWithoutActivatingLinks() {
        if (roles.state() != SmsRoleState.Held) grantRole()
        assertEquals(SmsRoleState.Held, roles.state())
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.cancelAll()
        deleteSyntheticMessages()
        try {
            val risky = "BBVA: Su tarjeta sera suspendida hoy. Actualice sus datos en http://bbva-pe.top"
            val harmless = "Te quiero mucho, cuidate"

            // El que no es de riesgo alto no avisa (el modelo lo marca, pero ninguna señal lo respalda: nivel medio).
            deliver(SENDER_F, harmless)
            deliver(SENDER_F, risky)

            // Las dos entregas se procesan en orden: cuando aparece la alerta, la primera ya se evaluó.
            waitUntil { riskAlerts(manager).isNotEmpty() && messageNotifications(manager).isNotEmpty() }
            val alerts = riskAlerts(manager)
            assertEquals("Solo el de riesgo alto debía dar alerta de riesgo", 1, alerts.size)
            assertEquals("El otro avisa como un mensaje cualquiera (#49)", 1, messageNotifications(manager).size)
            val notification = alerts.single().notification

            // Indica el riesgo, con el remitente y el texto del mensaje.
            val extras = notification.extras
            assertEquals(context.getString(R.string.alert_title), extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
            assertEquals("+$SENDER_F — $risky", extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString())
            // En la pantalla de bloqueo, la versión pública no dice ni el remitente ni el texto.
            assertEquals(android.app.Notification.VISIBILITY_PRIVATE, notification.visibility)
            val publicText = notification.publicVersion.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString()
            assertEquals(context.getString(R.string.alert_public_text), publicText)
            assertTrue(SENDER_F !in publicText && "bbva-pe.top" !in publicText)

            // Tocarla abre la conversación, con el aviso de riesgo; de ahí, "Ver riesgo" abre el detalle: el riesgo, el mensaje tal
            // cual y por qué se avisó.
            tap(alerts.single())
            compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.messages_notice_action)) > 0 && count(risky) > 0 }
            // La conversación tiene dos avisos (el otro mensaje es de riesgo medio); el riesgoso llegó después, así que es el último.
            compose.onAllNodesWithText(context.getString(R.string.messages_notice_action)).onLast().performClick()
            compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.detail_title)) > 0 && count(risky) > 0 }
            compose.onNodeWithText(context.getString(R.string.detail_banner_high)).assertIsDisplayed()
            compose.onNodeWithText(risky).assertIsDisplayed()

            // #11: nombra cada señal detectada con la parte del mensaje que la motivó, y no presenta la sospecha como certeza.
            val named = mapOf(
                R.string.detail_reason_urgency to "sera suspendida",
                R.string.detail_reason_impersonation to "BBVA",
                R.string.detail_reason_link to "http://bbva-pe.top",
                R.string.detail_reason_data_request to "Actualice sus datos",
            )
            for ((reason, fragment) in named) {
                compose.onNodeWithText(context.getString(reason)).assertIsDisplayed()
                compose.onNodeWithText(context.getString(R.string.detail_found_in_message, fragment)).assertIsDisplayed()
            }
            compose.onNodeWithText(context.getString(R.string.detail_disclaimer)).assertIsDisplayed()
            // "Este mensaje es seguro" es lo que diría la persona (mockup), no una afirmación de la app: no entra en la regla de certeza.
            val shown = visibleTexts().filter { it != risky && it != context.getString(R.string.detail_button_mark_safe) }
            assertTrue("Debía haber textos en pantalla", shown.size > 8)
            for (text in shown) {
                assertEquals(text, emptyList<String>(), PlainLanguage.violations(text))
            }

            // Sin activar enlaces: el enlace del mensaje es texto plano y tocarlo no abre nada ni saca de la app.
            compose.onNodeWithText(risky).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            compose.onNodeWithText(risky).performClick()
            Thread.sleep(1_500)
            val resumed = Shell.run("dumpsys activity activities").lines().first { "ResumedActivity" in it }
            assertTrue("Debía seguir en la app y no abrir un navegador: $resumed", context.packageName in resumed)
            compose.onNodeWithText(context.getString(R.string.detail_title)).assertIsDisplayed()
        } finally {
            manager.cancelAll()
            finishOpenActivities()
            deleteSyntheticMessages()
        }
    }

    /**
     * #13 de punta a punta: la persona elige el modo en Ajustes, queda guardado, y el receptor lo consulta **antes de actuar** sobre
     * un mensaje de riesgo alto: "preguntar" muestra las opciones, "automático" actúa, "solo avisarme" no toca el mensaje. Con
     * cualquier modo la alerta aparece, y un mensaje que no es de riesgo alto no dispara ninguna acción. El ejecutor se sustituye por
     * uno que anota la decisión (bloquear y retirar son #14 y #15). Datos sintéticos, que se borran al terminar.
     */
    @Test
    fun thenTheChosenModeDecidesWhatHappensToARiskyMessage() {
        if (roles.state() != SmsRoleState.Held) grantRole()
        assertEquals(SmsRoleState.Held, roles.state())
        val settings = DataStoreMitigationSettings(context)
        val database = HistoryDatabase.get(context)
        val history = RoomAnalysisHistory(database)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val decisions = java.util.Collections.synchronizedList(mutableListOf<MitigationDecision>())
        AnalysisPipeline.replaceMitigator { decision, _ -> decisions += decision }
        settings.setMode(MitigationMode.Ask)
        database.clearAllTables()
        manager.cancelAll()
        deleteSyntheticMessages()
        fun alerts() = riskAlerts(manager).size
        fun choose(mode: MitigationMode, title: Int) {
            compose.onNodeWithText(context.getString(title)).performClick()
            waitUntil { settings.mode() == mode }
            compose.onNodeWithText(context.getString(title)).assertIsSelected()
        }
        try {
            val risky = "BBVA: Su tarjeta sera suspendida hoy. Actualice sus datos en http://bbva-pe.top"
            val harmless = "Te quiero mucho, cuidate"

            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.messages_title)) > 0 }
                compose.onNodeWithText(context.getString(R.string.nav_settings)).performClick()
                compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.settings_mode_ask_title)) > 0 }

                // Sin elegir otra cosa, el modo es "preguntar".
                compose.waitUntil(TIMEOUT_MS) {
                    compose.onNodeWithText(context.getString(R.string.settings_mode_ask_title))
                        .fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true
                }
                deliver(SENDER_F, risky, secondsAfterSentAt = 1)
                waitUntil { alerts() == 1 }
                assertEquals(listOf(MitigationDecision.AskUser), decisions.toList())

                // "Protegerme automáticamente": actúa, y avisa igual.
                choose(MitigationMode.Automatic, R.string.settings_mode_automatic_title)
                deliver(SENDER_F, risky, secondsAfterSentAt = 2) // otra hora: el mismo mensaje a la misma hora se descarta como duplicado
                waitUntil { alerts() == 2 }
                assertEquals(listOf(MitigationDecision.AskUser, MitigationDecision.Act), decisions.toList())

                // "Solo avisarme": avisa, pero no se toca el mensaje.
                choose(MitigationMode.Disabled, R.string.settings_mode_disabled_title)
                deliver(SENDER_F, risky, secondsAfterSentAt = 3)
                waitUntil { alerts() == 3 }
                assertEquals("Con «solo avisarme» no debía haber acción", 2, decisions.size)

                // Con el modo automático, un mensaje que no es de riesgo alto no dispara nada (se espera a que ya esté evaluado).
                choose(MitigationMode.Automatic, R.string.settings_mode_automatic_title)
                deliver(SENDER_F, harmless)
                waitUntil { history.all().size == 4 }
                Thread.sleep(700)
                assertEquals("El de riesgo medio no debía disparar una acción", 2, decisions.size)
                assertEquals("Ni avisar", 3, alerts())
            }
            // La elección sigue guardada fuera de la pantalla.
            assertEquals(MitigationMode.Automatic, DataStoreMitigationSettings(context).mode())
        } finally {
            AnalysisPipeline.replaceMitigator(null)
            settings.setMode(MitigationMode.Ask)
            manager.cancelAll()
            database.clearAllTables()
            finishOpenActivities()
            deleteSyntheticMessages()
        }
    }

    /**
     * Toca un aviso, como lo hace la persona. Desde Android 14, `PendingIntent.send` desde una prueba (que no está en primer plano) no
     * abre la actividad si no se permite de forma explícita.
     */
    // La constante está deprecada en la API 36 a favor de otras que no existen en Android 14 y 15, donde también se corre.
    @Suppress("DEPRECATION")
    private fun tap(notification: android.service.notification.StatusBarNotification) {
        val options = if (android.os.Build.VERSION.SDK_INT >= 34) {
            android.app.ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else {
            null
        }
        notification.notification.contentIntent.send(context, 0, null, null, null, null, options)
    }

    /**
     * Toca una alerta de riesgo y llega al detalle del análisis. Tocarla abre la conversación del mensaje, donde "Ver riesgo" abre el
     * detalle; si el mensaje ya no está en el buzón (el modo automático lo retiró), abre el detalle directo.
     */
    private fun openDetailFromAlert(notification: android.service.notification.StatusBarNotification, text: String, messageStillThere: Boolean) {
        tap(notification)
        if (messageStillThere) {
            compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.messages_notice_action)) > 0 && count(text) > 0 }
            compose.onAllNodesWithText(context.getString(R.string.messages_notice_action)).onLast().performClick()
        }
    }

    private fun ourNotifications(manager: android.app.NotificationManager) =
        manager.activeNotifications.filter { it.packageName == context.packageName }

    /** Los avisos de mensaje (#49) llevan la etiqueta "message"; las alertas de riesgo no llevan etiqueta. */
    private fun messageNotifications(manager: android.app.NotificationManager) = ourNotifications(manager).filter { it.tag == "message" }

    private fun riskAlerts(manager: android.app.NotificationManager) = ourNotifications(manager).filter { it.tag == null }

    /**
     * #49 y #50 de punta a punta: cada SMS que no es de riesgo alto avisa con su remitente y el inicio del texto (y en la pantalla de
     * bloqueo, solo "Mensaje nuevo"); el de riesgo alto muestra solo su alerta. Los mensajes llegan sin leer y la lista lo dice; tocar
     * el aviso abre la conversación, que marca los mensajes como leídos y descarta su aviso, y lo mismo pasa con la alerta al abrir la
     * conversación del mensaje de riesgo alto. Datos sintéticos, que se borran al terminar.
     */
    @Test
    fun thenEachMessageIsNotifiedAndOpeningItsConversationReadsItAndClearsItsNotification() {
        if (roles.state() != SmsRoleState.Held) grantRole()
        assertEquals(SmsRoleState.Held, roles.state())
        val settings = DataStoreMitigationSettings(context)
        val database = HistoryDatabase.get(context)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        database.clearAllTables()
        manager.cancelAll()
        deleteBulkMessages()
        settings.setMode(MitigationMode.Ask) // el de riesgo alto no se toca
        try {
            val harmless = "Te quiero mucho, cuidate"
            val risky = "BBVA: Su tarjeta sera suspendida hoy. Actualice sus datos en http://bbva-pe.top"
            deliver(UNREAD_HARMLESS, harmless)
            deliver(UNREAD_RISKY, risky)

            waitUntil { messageNotifications(manager).size == 1 && riskAlerts(manager).size == 1 }
            Thread.sleep(500) // un instante por si sobrara un segundo aviso del de riesgo alto

            // El de riesgo alto muestra solo su alerta: no hay un aviso de mensaje para él.
            assertEquals("Un aviso de mensaje, el del que no es de riesgo alto", 1, messageNotifications(manager).size)
            assertEquals("Una alerta de riesgo, la del de riesgo alto", 1, riskAlerts(manager).size)
            val notice = messageNotifications(manager).single()
            val extras = notice.notification.extras
            assertEquals("+$UNREAD_HARMLESS", extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
            assertEquals(harmless, extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString())
            assertEquals("messages", notice.notification.channelId)
            // En la pantalla de bloqueo, solo dice que hay un mensaje nuevo: ni remitente ni texto.
            assertEquals(android.app.Notification.VISIBILITY_PRIVATE, notice.notification.visibility)
            val publicTitle = notice.notification.publicVersion.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString()
            assertEquals(context.getString(R.string.message_notification_public), publicTitle)
            assertTrue(UNREAD_HARMLESS !in publicTitle && harmless !in publicTitle)

            // Los dos llegaron sin leer.
            assertEquals(false, inbox(UNREAD_HARMLESS).single().read)
            assertEquals(false, inbox(UNREAD_RISKY).single().read)

            // Sin `use`: tocar el aviso abre la app con otra intención y el escenario no podría cerrarla; se cierra en `finally`.
            ActivityScenario.launch(MainActivity::class.java).let {
                openMessagesTab()
                // La lista distingue las conversaciones con mensajes sin leer, con su cuenta. Se compara con lo que dice el buzón y no con un
                // número fijo: otras pruebas pudieron dejar mensajes sin leer en el emulador.
                fun unreadOf(sender: String) =
                    TelephonyConversationRepository(context).conversations().first { it.address == "+$sender" }.unreadCount
                fun hasCounter(sender: String): Boolean {
                    val n = unreadOf(sender)
                    return n > 0 && compose.onAllNodes(
                        hasText("+$sender") and hasContentDescription(context.getString(R.string.messages_unread_description, n)),
                    ).fetchSemanticsNodes().isNotEmpty()
                }
                assertTrue("El mensaje debía llegar sin leer", unreadOf(UNREAD_HARMLESS) >= 1 && unreadOf(UNREAD_RISKY) >= 1)
                compose.waitUntil(TIMEOUT_MS) { hasCounter(UNREAD_HARMLESS) && hasCounter(UNREAD_RISKY) }

                // Tocar el aviso abre la conversación del mensaje; el mensaje queda leído y su aviso desaparece.
                tap(notice)
                compose.waitUntil(TIMEOUT_MS) { count(harmless) > 0 }
                waitUntil { inbox(UNREAD_HARMLESS).single().read }
                waitUntil { messageNotifications(manager).isEmpty() }
                assertEquals("La alerta del otro mensaje no se toca", 1, riskAlerts(manager).size)

                // Al volver a la lista, solo queda un contador: el del mensaje de riesgo alto, que todavía no se abrió.
                compose.onNodeWithContentDescription(context.getString(R.string.conversation_back)).performClick()
                compose.waitUntil(TIMEOUT_MS) { hasCounter(UNREAD_RISKY) && !hasCounter(UNREAD_HARMLESS) }

                // Abrir la conversación del de riesgo alto lo lee y descarta también su alerta.
                compose.onNodeWithText("+$UNREAD_RISKY").performClick()
                compose.waitUntil(TIMEOUT_MS) { count(risky) > 0 }
                waitUntil { inbox(UNREAD_RISKY).single().read }
                waitUntil { riskAlerts(manager).isEmpty() }
                compose.onNodeWithContentDescription(context.getString(R.string.conversation_back)).performClick()
                compose.waitUntil(TIMEOUT_MS) { !hasCounter(UNREAD_RISKY) && !hasCounter(UNREAD_HARMLESS) }
            }
        } finally {
            manager.cancelAll()
            settings.setMode(MitigationMode.Ask)
            database.clearAllTables()
            finishOpenActivities()
            deleteBulkMessages()
        }
    }

    /**
     * Un mensaje corto, que el modelo no evalúa, también avisa (#49): no se queda en silencio por no tener análisis. Y uno enviado por
     * la persona no cuenta como sin leer (#50). Datos sintéticos.
     */
    @Test
    fun thenAShortMessageIsAlsoNotifiedAndASentOneIsNeverUnread() {
        if (roles.state() != SmsRoleState.Held) grantRole()
        assertEquals(SmsRoleState.Held, roles.state())
        val database = HistoryDatabase.get(context)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        database.clearAllTables()
        manager.cancelAll()
        deleteBulkMessages()
        try {
            deliver(UNREAD_SHORT, "Hola ya") // demasiado corto: no se evalúa
            waitUntil { messageNotifications(manager).size == 1 }
            assertTrue("Sin análisis no hay alerta de riesgo", riskAlerts(manager).isEmpty())
            assertEquals("Hola ya", messageNotifications(manager).single().notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString())
            assertTrue("Y no entra en el historial", HistoryDatabase.get(context).dao().messageCount() == 0)

            insertSent(UNREAD_SENT, "Un mensaje que envié yo", System.currentTimeMillis())
            val unread = TelephonyConversationRepository(context).conversations().associate { it.address to it.unreadCount }
            assertEquals(1, unread["+$UNREAD_SHORT"])
            val rows = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.TYPE, Telephony.Sms.READ),
                "${Telephony.Sms.ADDRESS} = ?", arrayOf("+$UNREAD_SENT"), null,
            )!!.use { c -> generateSequence { if (c.moveToNext()) "hilo=${c.getLong(0)} tipo=${c.getInt(1)} leido=${c.getInt(2)}" else null }.toList() }
            assertEquals("Lo enviado no queda sin leer; filas: $rows", 0, unread["+$UNREAD_SENT"])
        } finally {
            manager.cancelAll()
            database.clearAllTables()
            deleteBulkMessages()
        }
    }

    /**
     * Desde Android 13 hace falta el permiso de notificaciones (#49, #10): sin él no se muestra ningún aviso ni alerta, y al concederlo
     * se muestran. Solo corre en Android 13 o superior y con la instalación limpia (el permiso sin conceder). Datos sintéticos.
     */
    @Test
    fun roleThenNotificationsFollowTheAndroid13Permission() {
        assumeTrue("Solo Android 13 o superior", android.os.Build.VERSION.SDK_INT >= 33)
        assumeTrue(
            "El permiso ya estaba concedido: desinstala la app para repetir esta prueba",
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
        if (roles.state() != SmsRoleState.Held) grantRole()
        val database = HistoryDatabase.get(context)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        database.clearAllTables()
        manager.cancelAll()
        deleteSyntheticMessages()
        try {
            val harmless = "Te quiero mucho, cuidate"
            val risky = "BBVA: Su tarjeta sera suspendida hoy. Actualice sus datos en http://bbva-pe.top"

            // Sin el permiso: los mensajes se reciben y se analizan, pero no hay ninguna notificación.
            deliver(SENDER_F, harmless)
            deliver(SENDER_E, risky)
            waitUntil { database.dao().messageCount() == 2 }
            Thread.sleep(800)
            assertTrue(
                "Sin el permiso no debe haber notificaciones: ${ourNotifications(manager).map { "${it.tag}/${it.id}/${it.notification.channelId}/enabled=${androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()}" }}",
                ourNotifications(manager).isEmpty(),
            )
            assertEquals("El mensaje llega igual", 1, inbox(SENDER_F).size)

            // Con el permiso concedido, los avisos y la alerta se muestran.
            Shell.run("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            deliver(SENDER_F, harmless, secondsAfterSentAt = 5)
            deliver(SENDER_E, risky, secondsAfterSentAt = 5)
            waitUntil { messageNotifications(manager).size == 1 && riskAlerts(manager).size == 1 }
        } finally {
            manager.cancelAll()
            database.clearAllTables()
            deleteSyntheticMessages()
        }
    }

    /** La app se abre en Inicio (#17): pasa a la pestaña de mensajes. */
    private fun openMessagesTab() {
        compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.nav_messages)) > 0 }
        compose.onNodeWithText(context.getString(R.string.nav_messages)).performClick()
    }

    private fun isBlocked(sender: String) = android.provider.BlockedNumberContract.isBlocked(context, "+$sender")

    private fun unblock(sender: String) = android.provider.BlockedNumberContract.unblock(context, "+$sender")

    /**
     * #14, #15, #16 y #28 de punta a punta, en modo automático: un SMS de riesgo alto bloquea al remitente en el sistema y se retira
     * del buzón; la alerta abre su detalle aunque el mensaje ya no esté, con las acciones hechas; "Marcar como seguro" desbloquea,
     * devuelve el mensaje a su conversación, ya leído, y el historial lo dice. Datos sintéticos, que se borran al terminar.
     */
    @Test
    fun thenTheAutomaticModeBlocksAndRemovesAndMarkingSafeRestoresBoth() {
        if (roles.state() != SmsRoleState.Held) grantRole()
        assertEquals(SmsRoleState.Held, roles.state())
        val settings = DataStoreMitigationSettings(context)
        val database = HistoryDatabase.get(context)
        val history = RoomAnalysisHistory(database)
        val log = RoomMitigationLog(database)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        database.clearAllTables()
        manager.cancelAll()
        deleteSyntheticMessages()
        unblock(SENDER_F)
        settings.setMode(MitigationMode.Automatic)
        try {
            val risky = "BBVA: Su tarjeta sera suspendida hoy. Actualice sus datos en http://bbva-pe.top"
            deliver(SENDER_F, risky, secondsAfterSentAt = 1)

            // La alerta sale después de actuar: cuando aparece, ya se bloqueó y se retiró.
            waitUntil { riskAlerts(manager).isNotEmpty() }
            assertTrue("El remitente debía quedar bloqueado en el sistema", isBlocked(SENDER_F))
            assertEquals("El mensaje debía salir del buzón; acciones: ${log.all()}", 0, inbox(SENDER_F).size)
            val record = history.all().single()
            val done = log.actionsOf(record.id).latest()
            assertEquals(ActionStatus.Completed, done.getValue(ActionType.BlockSender).status)
            assertEquals(ActionStatus.Completed, done.getValue(ActionType.RemoveMessage).status)
            assertEquals("La copia local conserva el mensaje", risky, record.body)

            // La alerta abre el detalle desde lo guardado aunque el mensaje ya no esté en el buzón; muestra lo hecho, sin botones de actuar.
            openDetailFromAlert(riskAlerts(manager).single(), risky, messageStillThere = false)
            compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.detail_title)) > 0 && count(risky) > 0 }
            compose.onNodeWithText(context.getString(R.string.detail_actions_label).uppercase()).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.detail_action_block_done)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.detail_action_remove_done)).performScrollTo().assertIsDisplayed()
            assertEquals(0, count(context.getString(R.string.detail_button_block)))
            assertEquals(0, count(context.getString(R.string.detail_button_remove)))

            // "Marcar como seguro" deshace las dos acciones.
            compose.onNodeWithText(context.getString(R.string.detail_button_mark_safe)).performScrollTo().performClick()
            // Se confirma antes de deshacer nada.
            compose.onNodeWithText(context.getString(R.string.detail_confirm_title)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.detail_confirm_yes)).performClick()
            compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.detail_marked_safe_title)) > 0 }
            compose.onNodeWithText(context.getString(R.string.detail_reverted_label).uppercase()).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.detail_reverted_block)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.detail_reverted_remove)).performScrollTo().assertIsDisplayed()
            assertTrue("El remitente debía quedar desbloqueado", !isBlocked(SENDER_F))
            val restored = inbox(SENDER_F).single()
            assertEquals("El mensaje vuelve igual a su conversación", risky, restored.body)
            assertTrue("Y ya leído", restored.read)
            val after = log.actionsOf(record.id).latest()
            assertEquals(ActionStatus.Reverted, after.getValue(ActionType.BlockSender).status)
            assertEquals(ActionStatus.Reverted, after.getValue(ActionType.RemoveMessage).status)
            assertTrue(history.byId(record.id)?.markedSafeAtMillis != null)

            // El historial lo dice, y el mensaje reaparece en su conversación (la lista ya no es la de antes).
            compose.onNodeWithContentDescription(context.getString(R.string.conversation_back)).performClick()
            compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.messages_title)) > 0 }
            compose.onNodeWithText(context.getString(R.string.nav_history)).performClick()
            compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.history_mark_safe)) > 0 }
            compose.onNodeWithText(context.getString(R.string.history_mark_safe)).assertIsDisplayed()
        } finally {
            manager.cancelAll()
            settings.setMode(MitigationMode.Ask)
            unblock(SENDER_F)
            database.clearAllTables()
            finishOpenActivities()
            deleteSyntheticMessages()
        }
    }

    /**
     * #14 y #15 a elección de la persona: en modo "preguntar" no se toca nada hasta que toca los botones del detalle, y cada acción
     * se refleja en el sistema y en lo que se muestra; en "solo avisarme" no se actúa solo, pero el detalle ofrece las acciones y la persona puede pedirlas (#29). Datos sintéticos.
     */
    @Test
    fun thenAskModeActsOnlyWhenThePersonChoosesAndTheOnlyNotifyModeNeverActs() {
        if (roles.state() != SmsRoleState.Held) grantRole()
        assertEquals(SmsRoleState.Held, roles.state())
        val settings = DataStoreMitigationSettings(context)
        val database = HistoryDatabase.get(context)
        val history = RoomAnalysisHistory(database)
        val log = RoomMitigationLog(database)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        database.clearAllTables()
        manager.cancelAll()
        deleteSyntheticMessages()
        unblock(SENDER_F)
        try {
            val risky = "BBVA: Su tarjeta sera suspendida hoy. Actualice sus datos en http://bbva-pe.top"

            // Preguntar: llega, avisa y no toca nada.
            settings.setMode(MitigationMode.Ask)
            deliver(SENDER_F, risky, secondsAfterSentAt = 2)
            waitUntil { riskAlerts(manager).isNotEmpty() }
            assertTrue(!isBlocked(SENDER_F))
            assertEquals(1, inbox(SENDER_F).size)
            val record = history.all().single()
            assertTrue("Sin acciones hasta que la persona decida", log.actionsOf(record.id).isEmpty())

            openDetailFromAlert(riskAlerts(manager).single(), risky, messageStillThere = true)
            compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.detail_title)) > 0 && count(risky) > 0 }
            compose.onNodeWithText(context.getString(R.string.detail_button_block)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.detail_button_remove)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.detail_button_mark_safe)).performScrollTo().assertIsDisplayed()

            // Bloquear: se bloquea, se muestra como hecho y el botón desaparece; el otro sigue.
            compose.onNodeWithText(context.getString(R.string.detail_button_block)).performScrollTo().performClick()
            compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.detail_action_block_done)) > 0 }
            assertTrue(isBlocked(SENDER_F))
            assertEquals(1, inbox(SENDER_F).size)
            assertEquals(0, count(context.getString(R.string.detail_button_block)))
            assertEquals(1, count(context.getString(R.string.detail_button_remove)))

            // Quitar: sale del buzón y se muestra como hecho; el registro conserva el mensaje.
            compose.onNodeWithText(context.getString(R.string.detail_button_remove)).performScrollTo().performClick()
            compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.detail_action_remove_done)) > 0 }
            assertEquals(0, inbox(SENDER_F).size)
            assertEquals(risky, history.byId(record.id)?.body)
            assertEquals(0, count(context.getString(R.string.detail_button_remove)))
            val done = log.actionsOf(record.id).latest()
            assertEquals(ActionStatus.Completed, done.getValue(ActionType.BlockSender).status)
            assertEquals(ActionStatus.Completed, done.getValue(ActionType.RemoveMessage).status)

            // Solo avisarme: llega otro, avisa, no se toca y el detalle no ofrece actuar.
            finishOpenActivities()
            manager.cancelAll()
            unblock(SENDER_F)
            settings.setMode(MitigationMode.Disabled)
            deliver(SENDER_F, risky, secondsAfterSentAt = 3)
            waitUntil { riskAlerts(manager).isNotEmpty() }
            assertTrue(!isBlocked(SENDER_F))
            assertEquals(1, inbox(SENDER_F).size)
            assertEquals("Solo el primer análisis tiene acciones", 2, history.all().size)
            assertEquals(0, log.actionsOf(history.all().first().id).size)

            openDetailFromAlert(riskAlerts(manager).single(), risky, messageStillThere = true)
            compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.detail_title)) > 0 && count(risky) > 0 }
            // #29: en «solo avisarme» no se actúa solo, pero el detalle ofrece las acciones y tocarlas es decisión de la persona.
            compose.onNodeWithText(context.getString(R.string.detail_button_block)).performScrollTo().assertIsDisplayed()
            assertEquals(1, count(context.getString(R.string.detail_button_remove)))
            assertEquals(0, count(context.getString(R.string.detail_actions_label).uppercase()))
            compose.onNodeWithText(context.getString(R.string.detail_button_block)).performScrollTo().performClick()
            waitUntil { isBlocked(SENDER_F) }
            val manual = log.actionsOf(history.all().first().id).latest()
            assertEquals(ActionStatus.Completed, manual.getValue(ActionType.BlockSender).status)
        } finally {
            manager.cancelAll()
            settings.setMode(MitigationMode.Ask)
            unblock(SENDER_F)
            database.clearAllTables()
            finishOpenActivities()
            deleteSyntheticMessages()
        }
    }

    /**
     * #20: con el Wi-Fi y los datos móviles apagados, un SMS se recibe, se clasifica y se alerta igual. Se comprueba que de verdad no
     * hay red (no basta con apagarla), y que la app ni siquiera declara el permiso de internet. Datos sintéticos.
     */
    @Test
    fun thenWithoutAnyConnectionAMessageIsReceivedClassifiedAndAlerted() {
        if (roles.state() != SmsRoleState.Held) grantRole()
        assertEquals(SmsRoleState.Held, roles.state())
        val settings = DataStoreMitigationSettings(context)
        val database = HistoryDatabase.get(context)
        val history = RoomAnalysisHistory(database)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        database.clearAllTables()
        manager.cancelAll()
        deleteSyntheticMessages()
        settings.setMode(MitigationMode.Disabled) // aquí solo se prueba recibir, clasificar y alertar
        try {
            // La app no tiene cómo usar la red: no declara el permiso de internet.
            val requested = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
                .requestedPermissions.orEmpty().toList()
            assertTrue("La app no debe declarar INTERNET: $requested", "android.permission.INTERNET" !in requested)

            Shell.run("svc wifi disable")
            Shell.run("svc data disable")
            // Apagarlas no es instantáneo, y mientras una cae la otra puede tomar el relevo un momento: se espera a que no haya
            // ninguna red activa durante tres segundos seguidos.
            val deadline = System.currentTimeMillis() + 30_000
            var quietSince = 0L
            while (System.currentTimeMillis() < deadline) {
                if (connectivity.activeNetwork == null) {
                    if (quietSince == 0L) quietSince = System.currentTimeMillis()
                    if (System.currentTimeMillis() - quietSince >= 3_000) break
                } else {
                    quietSince = 0L
                }
                Thread.sleep(200)
            }
            assertTrue("Debía no haber ninguna red activa", connectivity.activeNetwork == null && quietSince != 0L)

            val risky = "BBVA: Su tarjeta sera suspendida hoy. Actualice sus datos en http://bbva-pe.top"
            deliver(SENDER_F, risky, secondsAfterSentAt = 1)

            // Recibido: está en el buzón. Clasificado: queda su análisis. Alertado: aparece la alerta.
            waitUntil { riskAlerts(manager).isNotEmpty() }
            assertEquals("El mensaje debía llegar al buzón sin red", risky, inbox(SENDER_F).single().body)
            val record = history.all().single()
            assertEquals(RiskLevel.High, record.assessment.level)
            assertTrue(record.assessment.model.isSmishing)
            assertTrue("Debía seguir sin red", connectivity.activeNetwork == null)
        } finally {
            Shell.run("svc wifi enable")
            Shell.run("svc data enable")
            manager.cancelAll()
            settings.setMode(MitigationMode.Ask)
            database.clearAllTables()
            deleteSyntheticMessages()
        }
    }

    /**
     * #21: la demora desde que llega el SMS hasta que su nivel de riesgo queda guardado, con los mensajes controlados, y su P95
     * frente al objetivo de menos de un segundo. Cada mensaje entra por el receptor real (guardar en el buzón, analizar, guardar el
     * resultado). Se escribe un reporte sin los mensajes. **El resultado vale para el dispositivo donde se corre**: un emulador no
     * representa un teléfono de gama baja; para eso, correr esta misma prueba en el teléfono (ver docs/demora-y-sin-internet.md).
     */
    @Test
    fun thenTheLatencyFromReceptionToTheRiskLevelIsMeasured() {
        if (roles.state() != SmsRoleState.Held) grantRole()
        assertEquals(SmsRoleState.Held, roles.state())
        val settings = DataStoreMitigationSettings(context)
        val database = HistoryDatabase.get(context)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val assessor = AnalysisPipeline.assessor(context)
        val csv = InstrumentationRegistry.getInstrumentation().context.assets.open("controlled_messages.csv").bufferedReader().use { it.readText() }
        // Solo los que el modelo evalúa: los demasiado cortos no dejan análisis y no hay cuándo medir.
        val messages = com.app.smishing.ControlledMessages.parse(csv).map { it.message }
            .filter { assessor.assess(it) is com.app.smishing.domain.risk.RiskResult.Assessed }
        assertTrue("Debía haber una muestra grande: ${messages.size}", messages.size >= 40)
        database.clearAllTables()
        manager.cancelAll()
        deleteSyntheticMessages()
        settings.setMode(MitigationMode.Disabled) // no se bloquea ni se retira nada mientras se mide
        val endToEnd = mutableListOf<Double>()
        val analysisOnly = mutableListOf<Double>()
        try {
            var counter = 0
            repeat(ROUNDS) {
                for (text in messages) {
                    // Un remitente distinto cada vez: lo repetido (mismo remitente, hora y texto) se descarta como duplicado.
                    val sender = "999%08d".format(10_000_000 + counter++)
                    val before = database.dao().messageCount()
                    val start = System.nanoTime()
                    deliver(sender, *SmsPdu.parts(text).toTypedArray())
                    val deadline = start + 10_000_000_000L
                    while (database.dao().messageCount() == before && System.nanoTime() < deadline) Thread.sleep(0, 300_000)
                    assertTrue("El análisis no quedó guardado", database.dao().messageCount() > before)
                    endToEnd += (System.nanoTime() - start) / 1e6

                    val t0 = System.nanoTime()
                    assessor.assess(text)
                    analysisOnly += (System.nanoTime() - t0) / 1e6
                }
            }
            manager.cancelAll()

            fun percentile(values: List<Double>, p: Double): Double = values.sorted()[Math.ceil(p * values.size).toInt().coerceAtLeast(1) - 1]
            fun stats(values: List<Double>) = JSONObject()
                .put("count", values.size)
                .put("p50Ms", percentile(values, 0.50))
                .put("p95Ms", percentile(values, 0.95))
                .put("p99Ms", percentile(values, 0.99))
                .put("maxMs", values.max())
                .put("firstMs", values.first())
            val report = JSONObject()
                .put("issue", 21)
                .put("device", DeviceReport.deviceInfo())
                .put("targetP95Ms", 1000)
                .put("messagesPerRound", messages.size)
                .put("rounds", ROUNDS)
                .put("receptionToRiskLevel", stats(endToEnd))
                .put("riskAssessmentOnly", stats(analysisOnly))
                .put("note", "Reception to risk level saved, through the real receiver. The first delivery is not a cold start: the model was loaded by earlier tests in the same process. Poll resolution is about 1 ms.")
            DeviceReport.write(context, "latencia-api${android.os.Build.VERSION.SDK_INT}.json", report, "Latency")

            val p95 = percentile(endToEnd, 0.95)
            assertTrue("P95 de la recepción al nivel de riesgo: $p95 ms, el objetivo es menos de 1000 ms", p95 < 1000.0)
        } finally {
            manager.cancelAll()
            settings.setMode(MitigationMode.Ask)
            database.clearAllTables()
            deleteBulkMessages()
        }
    }

    /** Todos los textos que muestra la pantalla ahora, en el árbol sin fusionar. */
    private fun visibleTexts(): List<String> {
        fun walk(node: androidx.compose.ui.semantics.SemanticsNode): List<String> =
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.children.flatMap(::walk)
        return walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
    }

    private fun finishOpenActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).forEach { it.finish() }
        }
    }

    /**
     * #12 de punta a punta: cada SMS analizado queda en el historial local con su resultado, su nivel, su explicación y la versión
     * del modelo; se consulta desde la pestaña Historial; y **sigue ahí aunque el mensaje se borre del buzón**. Un mensaje
     * demasiado corto, que el modelo no evalúa, no entra. Datos sintéticos, que se borran al terminar.
     */
    @Test
    fun thenTheHistoryKeepsEachAnalysisLocallyAndItSurvivesDeletingTheMessage() {
        if (roles.state() != SmsRoleState.Held) grantRole()
        assertEquals(SmsRoleState.Held, roles.state())
        val database = HistoryDatabase.get(context)
        val history = RoomAnalysisHistory(database)
        database.clearAllTables()
        deleteSyntheticMessages()
        try {
            val risky = "BBVA: Su tarjeta sera suspendida hoy. Actualice sus datos en http://bbva-pe.top"
            val harmless = "Te quiero mucho, cuidate"
            val tooShort = "Hola ya"
            deliver(SENDER_F, risky)
            deliver(SENDER_F, harmless)
            deliver(SENDER_F, tooShort)

            // Las tres entregas se procesan en orden: cuando están los dos primeros, la tercera ya se evaluó (y no debe haber entrado).
            waitUntil { history.all().size >= 2 }
            Thread.sleep(1_000)
            val all = history.all()
            assertEquals("Entran los evaluados y no el demasiado corto", 2, all.size)

            // Lo que pide la historia: resultado, nivel, explicación y versión del modelo.
            val riskyRecord = all.single { it.body == risky }
            val a = riskyRecord.assessment
            assertEquals("+$SENDER_F", riskyRecord.sender)
            assertEquals(RiskLevel.High, a.level)
            assertEquals("risk.model_and_evidence", a.rule.id)
            assertTrue(a.model.isSmishing)
            assertTrue(a.model.score.toDouble() > a.model.threshold)
            assertEquals("1.0.0", a.model.modelVersion)
            assertEquals("1.1.0", a.model.preprocessingVersion)
            assertEquals("1.1.0", a.signals.rulesVersion)
            assertEquals("1.0.0", a.policyVersion)
            assertEquals(
                mapOf(
                    SignalType.Urgency to "sera suspendida",
                    SignalType.Impersonation to "BBVA",
                    SignalType.Link to "http://bbva-pe.top",
                    SignalType.DataRequest to "Actualice sus datos",
                ),
                a.signals.signals.associate { it.type to it.fragment },
            )
            assertEquals(RiskLevel.Medium, all.single { it.body == harmless }.assessment.level)
            assertTrue("El mensaje corto no debía entrar", all.none { it.body == tooShort })

            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.messages_title)) > 0 }
                compose.onNodeWithText(context.getString(R.string.nav_history)).performClick()

                // La lista del historial: los dos mensajes, con su marca de estado.
                compose.waitUntil(TIMEOUT_MS) { count(risky) > 0 }
                assertEquals(2, compose.onAllNodesWithText("+$SENDER_F").fetchSemanticsNodes().size)
                compose.onNodeWithText(context.getString(R.string.history_chip_high)).assertIsDisplayed()
                compose.onNodeWithText(context.getString(R.string.history_chip_medium)).assertIsDisplayed()

                // Su detalle sale de lo guardado: nivel, mensaje y explicación.
                compose.onNodeWithText(risky).performClick()
                compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.detail_title)) > 0 }
                compose.onNodeWithText(context.getString(R.string.detail_banner_high)).assertIsDisplayed()
                compose.onNodeWithText(context.getString(R.string.detail_found_in_message, "Actualice sus datos")).assertIsDisplayed()
                compose.onNodeWithContentDescription(context.getString(R.string.conversation_back)).performClick()
                compose.waitUntil(TIMEOUT_MS) { count(risky) > 0 }

                // Se borra el mensaje del buzón: el registro sigue en el historial y sigue abriéndose.
                deleteSyntheticMessages()
                assertEquals("El mensaje debía haber salido del buzón", 0, inbox(SENDER_F).size)
                assertEquals(2, history.all().size)
                compose.onNodeWithText(risky).performClick()
                compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.detail_title)) > 0 }
                compose.onNodeWithText(context.getString(R.string.detail_banner_high)).assertIsDisplayed()
                compose.onNodeWithText(risky).assertIsDisplayed()
            }
        } finally {
            database.clearAllTables()
            finishOpenActivities()
            deleteSyntheticMessages()
        }
    }

    private fun insertSent(sender: String, body: String, at: Long) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, "+$sender")
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, at)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        checkNotNull(context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)) { "El buzón no guardó el mensaje enviado" }
    }

    private fun bulkAddress(index: Int) = "$BULK_PREFIX%08d".format(10_000_000 + index * 1237)

    private fun deleteBulkMessages() {
        context.contentResolver.delete(Uri.parse("content://sms"), "${Telephony.Sms.ADDRESS} LIKE ?", arrayOf("$BULK_PREFIX%"))
    }

    private data class Stored(val body: String, val threadId: Long, val sentAt: Long, val read: Boolean)

    private fun deliver(sender: String, vararg texts: String, secondsAfterSentAt: Int = 0) {
        val pdus = texts.mapIndexed { i, text -> SmsPdu.deliver(sender, text, secondsAfterSentAt + i) }.toTypedArray()
        val intent = Intent(Telephony.Sms.Intents.SMS_DELIVER_ACTION).putExtra("pdus", pdus).putExtra("format", "3gpp")
        SmsDeliverReceiver().onReceive(context, intent)
    }

    private fun inbox(sender: String): List<Stored> =
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.BODY, Telephony.Sms.THREAD_ID, Telephony.Sms.DATE_SENT, Telephony.Sms.READ),
            "${Telephony.Sms.ADDRESS} = ?",
            arrayOf("+$sender"),
            null,
        )!!.use { c ->
            generateSequence { if (c.moveToNext()) Stored(c.getString(0), c.getLong(1), c.getLong(2), c.getInt(3) == 1) else null }.toList()
        }

    private fun deleteSyntheticMessages() {
        for (sender in listOf(SENDER_A, SENDER_B, SENDER_C, SENDER_D, SENDER_E, SENDER_F)) {
            context.contentResolver.delete(Uri.parse("content://sms"), "${Telephony.Sms.ADDRESS} = ?", arrayOf("+$sender"))
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue("Se agotó la espera", condition())
    }

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    private fun grantRole() {
        Shell.run("cmd role add-role-holder --user 0 $ROLE ${context.packageName}")
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (roles.state() != SmsRoleState.Held && System.currentTimeMillis() < deadline) Thread.sleep(100)
    }

    private companion object {
        const val ROLE = "android.app.role.SMS"
        // Remitentes ficticios (números de prueba, no de personas reales).
        const val SENDER_A = "10000000001"
        const val SENDER_B = "10000000002"
        const val SENDER_C = "10000000003"
        const val SENDER_D = "10000000004"
        const val SENDER_E = "10000000005"
        const val SENDER_F = "10000000006"
        // +999 no es un código de país asignado: son números imposibles, y bien distintos entre sí para que el proveedor
        // de SMS no los fusione en una sola conversación (compara los números de forma laxa).
        // El número del propio emulador: lo que se le envía, él mismo lo entrega.
        const val EMULATOR_NUMBER = "5554"
        const val ECHO_TEXT = "Prueba de envío desde la app."
        const val BULK_PREFIX = "+999"
        const val TIMEOUT_MS = 10_000L
        const val ROUNDS = 3
        // Remitentes propios de las pruebas de lo no leído (+999…): ver deleteBulkMessages.
        const val UNREAD_HARMLESS = "99900000002"
        const val UNREAD_RISKY = "99900000003"
        const val UNREAD_SHORT = "99900000004"
        const val UNREAD_SENT = "99900000005"
        const val NOTIFICATION_PERMISSION_TEST = "roleThenNotificationsFollowTheAndroid13Permission"

    }
}
