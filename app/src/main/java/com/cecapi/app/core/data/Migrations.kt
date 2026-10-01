package com.cecapi.app.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v2: users get a role and an origin/institution, and usernames become case-insensitive (UPPERCASE). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN rol TEXT NOT NULL DEFAULT 'usuario'")
        db.execSQL("ALTER TABLE usuarios ADD COLUMN origen TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE usuarios SET nombre_usuario = UPPER(nombre_usuario)")
        // The shared test account that already existed becomes an administrator.
        db.execSQL("UPDATE usuarios SET rol = 'administrador', origen = 'admin' WHERE nombre_usuario = 'CECAPI'")
    }
}

/**
 * v3: the audio exercises of Módulo 6 now carry a sound file and the volume of each ear, and the vibration
 * exercises get their own tables. The old voice-command exercises are dropped (their results go with them,
 * by cascade); the new bank is created the first time Actividades opens.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM resultados_ejercicios")
        db.execSQL("DELETE FROM ejercicios")
        db.execSQL("ALTER TABLE ejercicios ADD COLUMN archivo_sonido TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE ejercicios ADD COLUMN vol_izq_inicio REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ejercicios ADD COLUMN vol_der_inicio REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ejercicios ADD COLUMN vol_izq_fin REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ejercicios ADD COLUMN vol_der_fin REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ejercicios ADD COLUMN duracion_ms INTEGER NOT NULL DEFAULT 1500")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS ejercicios_vibracion (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "titulo TEXT NOT NULL, instruccion TEXT NOT NULL, respuesta_correcta TEXT NOT NULL, " +
                "nivel INTEGER NOT NULL, patron_ms TEXT NOT NULL, intensidad INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS resultados_vibracion (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "usuario_id INTEGER NOT NULL, ejercicio_vibracion_id INTEGER NOT NULL, " +
                "fue_correcto INTEGER NOT NULL, fecha_realizado INTEGER NOT NULL, " +
                "FOREIGN KEY(usuario_id) REFERENCES usuarios(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(ejercicio_vibracion_id) REFERENCES ejercicios_vibracion(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_resultados_vibracion_usuario_id ON resultados_vibracion (usuario_id)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_resultados_vibracion_ejercicio_vibracion_id " +
                "ON resultados_vibracion (ejercicio_vibracion_id)",
        )
    }
}

/** v4: roles get educador, and an alumno can be linked to the educador who teaches them. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN educador_id INTEGER")
    }
}

/** v5: a simple way for an alumno or an independent usuario to report a problem. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS incidencias (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "reportante_id INTEGER NOT NULL, destino_origen TEXT, mensaje TEXT NOT NULL, " +
                "fecha_reporte INTEGER NOT NULL, resuelta INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(reportante_id) REFERENCES usuarios(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_incidencias_reportante_id ON incidencias (reportante_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_incidencias_destino_origen ON incidencias (destino_origen)")
    }
}
