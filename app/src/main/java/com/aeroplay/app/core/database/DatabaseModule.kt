package com.aeroplay.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE watch_history ADD COLUMN playCount INTEGER NOT NULL DEFAULT 1")
            db.execSQL("""CREATE TABLE IF NOT EXISTS device_videos (
                videoId TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, fileName TEXT NOT NULL,
                thumbnailName TEXT NOT NULL, mimeType TEXT NOT NULL, durationMs INTEGER NOT NULL,
                width INTEGER NOT NULL, height INTEGER NOT NULL, sizeBytes INTEGER NOT NULL,
                importedAtEpochMs INTEGER NOT NULL)""")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AeroPlayDatabase {
        return Room.databaseBuilder(
            context,
            AeroPlayDatabase::class.java,
            "aeroplay_local.db"
        ).addMigrations(MIGRATION_1_2).build()
    }

    @Provides
    @Singleton
    fun provideDao(database: AeroPlayDatabase): AeroPlayDao = database.aeroPlayDao()
}
