package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BookFormat(val extension: String, val displayName: String) {
    EPUB("epub", "EPUB"),
    PDF("pdf", "PDF"),
    CBZ("cbz", "CBZ"),
    CBR("cbr", "CBR"),
    MD("md", "Markdown"),
    TXT("txt", "TXT"),
    FB2("fb2", "FB2");

    companion object {
        fun fromFilename(name: String): BookFormat? {
            val lower = name.lowercase()
            return when {
                lower.endsWith(".epub") -> EPUB
                lower.endsWith(".pdf") -> PDF
                lower.endsWith(".cbz") -> CBZ
                lower.endsWith(".cbr") -> CBR
                lower.endsWith(".md") || lower.endsWith(".markdown") -> MD
                lower.endsWith(".txt") -> TXT
                lower.endsWith(".fb2") -> FB2
                else -> null
            }
        }
    }
}

enum class ReadingStatus(val label: String) {
    UNREAD("Want to Read"),
    READING("Reading"),
    FINISHED("Finished")
}

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["contentHash"]),
        Index(value = ["title"]),
        Index(value = ["authors"]),
        Index(value = ["stackId"])
    ]
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val contentHash: String,
    val title: String,
    val authors: String,
    val series: String? = null,
    val seriesIndex: Float? = null,
    val format: BookFormat,
    val coverPath: String? = null,
    val addedAt: Long,
    val lastOpenedAt: Long? = null,
    val progress: Float = 0f,
    val status: ReadingStatus = ReadingStatus.UNREAD,
    val shelfOrder: Int = 0,
    val fileSizeBytes: Long = 0L,
    val pageCountEstimate: Int = 220,
    val folderTreeUri: String = "imported://documents",
    val relativeFolderPath: String = "",
    val hidden: Boolean = false,
    val stackId: String? = null,
    val stackTitle: String? = null,
    val isSample: Boolean = false
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val treeUri: String,
    val displayName: String,
    val addedAt: Long,
    val lastScannedAt: Long,
    val bookCount: Int = 0
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

@Entity(
    tableName = "book_collections",
    primaryKeys = ["bookId", "collectionId"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("collectionId")]
)
data class BookCollectionCrossRef(
    val bookId: Long,
    val collectionId: Long
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "book_tags",
    primaryKeys = ["bookId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tagId")]
)
data class BookTagCrossRef(
    val bookId: Long,
    val tagId: Long
)

@Entity(tableName = "themes")
data class ThemeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isDark: Boolean,
    val paletteJson: String,
    val builtIn: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "book_settings",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BookSettingsEntity(
    @PrimaryKey val bookId: Long,
    val themeId: String? = null,
    val fontOverride: String? = null,
    val layoutOverrideJson: String? = null
)

@Entity(
    tableName = "positions",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PositionEntity(
    @PrimaryKey val bookId: Long,
    val locatorJson: String,
    val updatedAt: Long
)

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val type: String,
    val colorRole: Int,
    val locatorJson: String,
    val quoteExact: String,
    val quotePrefix: String = "",
    val quoteSuffix: String = "",
    val noteMarkdown: String = "",
    val tags: String = "",
    val style: String = "HIGHLIGHT",
    val sticker: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "export_templates")
data class ExportTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val body: String
)

@Entity(
    tableName = "dictionaries",
    indices = [Index(value = ["language"]), Index(value = ["sortOrder"])]
)
data class DictionaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val language: String,
    val format: String,
    val storagePath: String,
    val entryCount: Int = 0,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val isBundled: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "vocabulary_words",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["word"]),
        Index(value = ["lemma"]),
        Index(value = ["nextReviewAt"])
    ]
)
data class VocabularyWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val lemma: String,
    val partOfSpeech: String = "",
    val definition: String,
    val exampleSentence: String = "",
    val contextSentence: String = "",
    val bookId: Long? = null,
    val bookTitle: String = "",
    val srsRepetitions: Int = 0,
    val srsIntervalDays: Int = 0,
    val srsEaseFactor: Float = 2.5f,
    val nextReviewAt: Long = System.currentTimeMillis(),
    val lastReviewedAt: Long? = null,
    val masteryLevel: String = "NEW",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "annotation_layers",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"]), Index(value = ["sortOrder"])]
)
data class AnnotationLayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val name: String,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1f,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "markup_objects",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookId", "pageIndex"]),
        Index(value = ["layerId"]),
        Index(value = ["parentGroupId"])
    ]
)
data class MarkupObjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val pageIndex: Int,
    val layerId: Long,
    val kind: String,
    val geometryJson: String,
    val styleJson: String,
    val label: String = "",
    val parentGroupId: String? = null,
    val zOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "page_scales",
    primaryKeys = ["bookId", "pageIndex"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PageScaleEntity(
    val bookId: Long,
    val pageIndex: Int,
    val pagePtsPerUnit: Float,
    val unit: String = "MM",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "reading_sessions",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookId", "dateIso"], unique = true),
        Index(value = ["dateIso"])
    ]
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val bookTitle: String,
    val dateIso: String,
    val startedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0L,
    val pagesTurned: Int = 0
)



