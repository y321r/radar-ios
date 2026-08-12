package com.radar.news.data.local

import android.content.Context
import androidx.room.Room

/** Android actual for building the shared Room database. */
fun createRadarDatabase(context: Context): RadarDatabase =
    Room.databaseBuilder(context, RadarDatabase::class.java, RadarDatabase.NAME)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
