package com.example.di

import android.content.Context
import com.example.data.annotations.AnnotationRepository
import com.example.data.db.MochiDatabase
import com.example.data.dictionary.DictionaryRepository
import com.example.data.dictionary.VocabularyRepository
import com.example.data.library.GutenbergRepository
import com.example.data.library.LibraryRepository
import com.example.data.preferences.MochiPreferences
import com.example.data.reader.ReaderRepository
import com.example.data.stats.ReadingStatsRepository
import com.example.data.studio.AnnotationStudioRepository

class AppContainer(context: Context) {
    val database: MochiDatabase = MochiDatabase.build(context)
    val preferences: MochiPreferences = MochiPreferences(context)
    val gutenbergRepository: GutenbergRepository = GutenbergRepository()
    val libraryRepository: LibraryRepository = LibraryRepository(
        context = context,
        bookDao = database.bookDao(),
        folderDao = database.folderDao(),
        collectionDao = database.collectionDao(),
        tagDao = database.tagDao(),
        themeDao = database.themeDao()
    )
    val readerRepository: ReaderRepository = ReaderRepository(
        context = context,
        bookDao = database.bookDao(),
        positionDao = database.positionDao(),
        bookSettingsDao = database.bookSettingsDao()
    )
    val annotationRepository: AnnotationRepository = AnnotationRepository(
        annotationDao = database.annotationDao(),
        exportTemplateDao = database.exportTemplateDao(),
        bookDao = database.bookDao()
    )
    val annotationStudioRepository: AnnotationStudioRepository = AnnotationStudioRepository(
        dao = database.annotationStudioDao()
    )
    val dictionaryRepository: DictionaryRepository = DictionaryRepository(
        context = context,
        dictionaryDao = database.dictionaryDao()
    )
    val vocabularyRepository: VocabularyRepository = VocabularyRepository(
        vocabularyDao = database.vocabularyDao()
    )
    val readingStatsRepository: ReadingStatsRepository = ReadingStatsRepository(
        dao = database.readingStatsDao()
    )
}
