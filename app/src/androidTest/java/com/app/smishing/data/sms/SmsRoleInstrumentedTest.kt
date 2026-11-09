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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
import com.app.smishing.data.messaging.TelephonyConversationRepository
import com.app.smishing.data.classification.OnDeviceClassifier
import com.app.smishing.data.history.HistoryDatabase
import com.app.smishing.data.history.RoomAnalysisHistory
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

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // 2. Sin rol, la app pide primero el rol: todavía no menciona los permisos.
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
            // Con el rol y los permisos, la app deja el onboarding y muestra los mensajes (#5).
            val messages = context.getString(R.string.messages_title)
            compose.waitUntil(TIMEOUT_MS) { count(messages) > 0 }
            // "Mensajes" es el título y también la pestaña de la barra inferior: basta con que esté.
            assertTrue(count(messages) >= 1)
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
                compose.waitUntil(TIMEOUT_MS) { count(arriving) > 0 }
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
        if (roles.state() != SmsRoleState.Held) grantRole()
        assertEquals(SmsRoleState.Held, roles.state())
        assertEquals("Con el rol, Android debía conceder también el permiso de enviar", true, hasSendPermission())
        deleteEmulatorMessages()
        try {
            val text = ECHO_TEXT
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.compose_action)) > 0 }
                compose.onNodeWithText(context.getString(R.string.compose_action)).performClick()

                compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.new_message_title)) > 0 }
                compose.onNodeWithText(context.getString(R.string.new_message_recipient_hint)).performTextInput(EMULATOR_NUMBER)
                compose.onNodeWithText(context.getString(R.string.new_message_body_hint)).performTextInput(text)
                compose.onNodeWithText(context.getString(R.string.new_message_send)).performClick()

                // Aparece en su conversación, a la derecha, con el número elegido en la barra.
                compose.waitUntil(TIMEOUT_MS) { count(context.getString(R.string.conversation_subtitle)) > 0 && count(text) > 0 }
                compose.onNodeWithText(EMULATOR_NUMBER).assertIsDisplayed()
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
            waitUntil { incomingCount(text) >= 1 }
            Thread.sleep(1_000)
            assertEquals("La copia recibida debía llegar una sola vez", 1, incomingCount(text))
        } finally {
            deleteEmulatorMessages()
        }
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
        context.contentResolver.delete(Uri.parse("content://sms"), "${Telephony.Sms.ADDRESS} LIKE ?", arrayOf("%$EMULATOR_NUMBER%"))
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
            waitUntil { manager.activeNotifications.any { it.packageName == context.packageName } }
            val alerts = manager.activeNotifications.filter { it.packageName == context.packageName }
            assertEquals("Solo el de riesgo alto debía avisar", 1, alerts.size)
            val notification = alerts.single().notification

            // Indica el riesgo, con el remitente y un motivo, y no revela el texto del mensaje.
            val extras = notification.extras
            assertEquals(context.getString(R.string.alert_title), extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
            assertEquals(
                "+$SENDER_F — ${context.getString(R.string.alert_reason_urgency)}",
                extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString(),
            )
            val visible = extras.keySet().mapNotNull { extras.get(it)?.toString() }.joinToString(" ")
            for (secret in listOf("bbva-pe.top", "Actualice", "tarjeta")) {
                assertTrue("La notificación no debía mostrar «$secret»", secret !in visible)
            }
            // En la pantalla de bloqueo, la versión pública no dice ni el remitente ni el motivo.
            assertEquals(android.app.Notification.VISIBILITY_PRIVATE, notification.visibility)
            val publicText = notification.publicVersion.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString()
            assertEquals(context.getString(R.string.alert_public_text), publicText)
            assertTrue(SENDER_F !in publicText)

            // Tocarla abre el detalle: el riesgo, el mensaje tal cual y por qué se avisó.
            notification.contentIntent.send()
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
            compose.onNodeWithText(context.getString(R.string.detail_summary_high)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.detail_disclaimer)).assertIsDisplayed()
            val shown = visibleTexts().filter { it != risky }
            assertTrue("Debía haber textos en pantalla", shown.size > 8)
            for (text in shown) {
                assertEquals(text, emptyList<String>(), PlainLanguage.violations(text))
            }

            // Sin activar enlaces: el enlace del mensaje es texto plano y tocarlo no abre nada ni saca de la app.
            compose.onNodeWithText(risky).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            compose.onNodeWithText(risky).performClick()
            Thread.sleep(1_500)
            val resumed = Shell.run("dumpsys activity activities").lines().first { "mResumedActivity" in it }
            assertTrue("Debía seguir en la app y no abrir un navegador: $resumed", context.packageName in resumed)
            compose.onNodeWithText(context.getString(R.string.detail_title)).assertIsDisplayed()
        } finally {
            manager.cancelAll()
            finishOpenActivities()
            deleteSyntheticMessages()
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
    }
}
