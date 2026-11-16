package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LocalDocumentEntity::class,
        DownloadTaskEntity::class,
        FolderEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PdfFlowDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun downloadDao(): DownloadDao
    abstract fun folderDao(): FolderDao

    companion object {
        @Volatile
        private var INSTANCE: PdfFlowDatabase? = null

        fun getInstance(context: Context): PdfFlowDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PdfFlowDatabase::class.java,
                    "pdf_flow_database.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
