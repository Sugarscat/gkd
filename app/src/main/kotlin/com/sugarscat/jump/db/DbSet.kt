package com.sugarscat.jump.db

import androidx.room.Room
import com.sugarscat.jump.app
import com.sugarscat.jump.util.dbFolder

object DbSet {

    private fun buildDb(): AppDb {
        return Room.databaseBuilder(
            com.sugarscat.jump.app, AppDb::class.java, dbFolder.resolve("gkd.db").absolutePath
        ).fallbackToDestructiveMigration().build()
    }

    private val db by lazy { buildDb() }
    val subsItemDao
        get() = db.subsItemDao()
    val subsConfigDao
        get() = db.subsConfigDao()
    val snapshotDao
        get() = db.snapshotDao()
    val clickLogDao
        get() = db.clickLogDao()
    val categoryConfigDao
        get() = db.categoryConfigDao()
    val activityLogDao
        get() = db.activityLogDao()
}