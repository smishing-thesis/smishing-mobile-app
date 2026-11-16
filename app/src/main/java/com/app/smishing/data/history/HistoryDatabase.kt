package com.app.smishing.data.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * La base local del historial de análisis (#12) y de las acciones de mitigación (#28). El esquema se exporta a `app/schemas`.
 * Versión 2: agrega las acciones y la marca de "seguro"; ver [MIGRATION_1_2]. Versión 3: agrega la cola de aportes pendientes (#71); ver
 * [MIGRATION_2_3].
 */
@Database(
    entities = [AnalyzedMessageEntity::class, AnalysisResultEntity::class, DetectedSignalEntity::class, MitigationActionEntity::class, PendingContributionEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class HistoryDatabase : RoomDatabase() {

    abstract fun dao(): HistoryDao

    companion object {
        const val FILE_NAME = "history.db"

        @Volatile
        private var instance: HistoryDatabase? = null

        /** La base de la app, abierta una sola vez por proceso. */
        fun get(context: Context): HistoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, HistoryDatabase::class.java, FILE_NAME)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }

        /** Una base en memoria, que desaparece al cerrarla: para las pruebas. */
        fun inMemory(context: Context): HistoryDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, HistoryDatabase::class.java).build()

        /** Una base en un archivo con ese nombre: para probar que lo guardado sobrevive a cerrar y volver a abrir. */
        fun inFile(context: Context, fileName: String): HistoryDatabase =
            Room.databaseBuilder(context.applicationContext, HistoryDatabase::class.java, fileName).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
