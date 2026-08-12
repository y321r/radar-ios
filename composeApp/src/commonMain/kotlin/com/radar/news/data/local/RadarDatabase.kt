package com.radar.news.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Version 2 adds `articles.seenInFeed` (S5). No migration is written: the builder uses
 * `fallbackToDestructiveMigration(dropAllTables = true)`, and the table is a 48-hour cache of
 * public headlines that one sync rebuilds.
 */
@Database(
    entities = [ArticleEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RadarDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao

    companion object {
        const val NAME = "radar.db"
    }
}
