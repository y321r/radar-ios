package com.radar.news.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/** iOS actual for building the shared Room database on the bundled SQLite driver. */
fun createRadarDatabase(): RadarDatabase =
    Room.databaseBuilder<RadarDatabase>(name = RadarDatabase.NAME)
        .setDriver(BundledSQLiteDriver())
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
