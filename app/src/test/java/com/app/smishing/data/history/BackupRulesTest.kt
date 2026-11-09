package com.app.smishing.data.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * El historial (#12) guarda el texto de mensajes en una base local. Estas pruebas fijan que esa base **no sale del teléfono**: ni a
 * la copia de seguridad en la nube de Google, ni a otro teléfono al migrar. Si alguien vuelve a poner reglas vacías, fallan.
 */
class BackupRulesTest {

    private fun file(name: String): File =
        listOf(File("src/main/$name"), File("app/src/main/$name")).first { it.exists() }

    private fun parse(name: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file(name)).documentElement

    private fun excludes(section: Element): List<Pair<String, String>> {
        val nodes = section.getElementsByTagName("exclude")
        return (0 until nodes.length).map { (nodes.item(it) as Element).let { e -> e.getAttribute("domain") to e.getAttribute("path") } }
    }

    private fun child(parent: Element, tag: String): Element = parent.getElementsByTagName(tag).item(0) as Element

    @Test
    fun theDatabaseIsExcludedFromTheCloudBackupOnAndroid12AndAbove() {
        val rules = parse("res/xml/data_extraction_rules.xml")
        assertTrue(("database" to ".") in excludes(child(rules, "cloud-backup")))
    }

    @Test
    fun theDatabaseIsExcludedFromTheDeviceTransferOnAndroid12AndAbove() {
        val rules = parse("res/xml/data_extraction_rules.xml")
        assertTrue(("database" to ".") in excludes(child(rules, "device-transfer")))
    }

    @Test
    fun theDatabaseIsExcludedFromTheBackupOnAndroid11AndBelow() {
        val rules = parse("res/xml/backup_rules.xml")
        assertTrue(("database" to ".") in excludes(rules))
    }

    @Test
    fun theManifestPointsAtBothRulesFiles() {
        val manifest = file("AndroidManifest.xml").readText()
        assertTrue("@xml/data_extraction_rules" in manifest)
        assertTrue("@xml/backup_rules" in manifest)
    }

    @Test
    fun noRuleBringsTheDatabaseBackIn() {
        for (name in listOf("res/xml/data_extraction_rules.xml", "res/xml/backup_rules.xml")) {
            val includes = parse(name).getElementsByTagName("include")
            val databaseIncludes = (0 until includes.length).count { (includes.item(it) as Element).getAttribute("domain") == "database" }
            assertEquals("$name vuelve a incluir la base de datos", 0, databaseIncludes)
        }
    }
}
