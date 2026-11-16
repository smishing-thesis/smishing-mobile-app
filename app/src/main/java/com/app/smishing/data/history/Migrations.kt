package com.app.smishing.data.history

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * De la versión 1 (#12) a la 2 (#28): agrega la tabla de acciones de mitigación y la marca de "seguro" del mensaje. **No toca
 * lo ya guardado**: los análisis de la versión 1 se conservan y quedan sin acciones y sin marca. El SQL es el del esquema
 * exportado `app/schemas/.../2.json`; `HistoryMigrationTest` lo comprueba contra datos reales de la versión 1.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `analyzed_message` ADD COLUMN `markedSafeAtMillis` INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `mitigation_action` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`analyzedMessageId` INTEGER NOT NULL, `type` TEXT NOT NULL, `status` TEXT NOT NULL, `note` TEXT, " +
                "`requestedAtMillis` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, " +
                "FOREIGN KEY(`analyzedMessageId`) REFERENCES `analyzed_message`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mitigation_action_analyzedMessageId` ON `mitigation_action` (`analyzedMessageId`)")
    }
}

/**
 * De la versión 2 a la 3 (#71): agrega la tabla de aportes pendientes. **No toca lo ya guardado**. El SQL es el del esquema exportado
 * `app/schemas/.../3.json`.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_contribution` (`analyzedMessageId` INTEGER NOT NULL, `queuedAtMillis` INTEGER NOT NULL, " +
                "PRIMARY KEY(`analyzedMessageId`), " +
                "FOREIGN KEY(`analyzedMessageId`) REFERENCES `analyzed_message`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}
