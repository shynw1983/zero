package com.zero.todo.data.local

import android.content.Context
import androidx.room.Room
import com.zero.todo.data.local.dao.FocusSessionDao
import com.zero.todo.data.local.dao.FolderDao
import com.zero.todo.data.local.dao.TagDao
import com.zero.todo.data.local.dao.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideZeroDatabase(@ApplicationContext context: Context): ZeroDatabase =
        Room.databaseBuilder(
            context,
            ZeroDatabase::class.java,
            ZeroDatabase.DATABASE_NAME
        )
            .addMigrations(ZeroDatabase.MIGRATION_1_2, ZeroDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideFolderDao(database: ZeroDatabase): FolderDao = database.folderDao()

    @Provides
    fun provideTaskDao(database: ZeroDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideTagDao(database: ZeroDatabase): TagDao = database.tagDao()

    @Provides
    fun provideFocusSessionDao(database: ZeroDatabase): FocusSessionDao = database.focusSessionDao()
}
