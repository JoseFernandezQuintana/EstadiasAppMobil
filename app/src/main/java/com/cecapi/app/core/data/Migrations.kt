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
