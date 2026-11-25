package com.example

import android.app.Application
import android.graphics.Bitmap
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.reader.PdfPageEngine
import com.example.data.reader.ReaderDocumentCache
import com.example.di.AppContainer
import com.example.theme.BundledThemes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MochiApplication : Application(), ImageLoaderFactory {
    val container: AppContainer by lazy { AppContainer(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Warm up container, bundled palettes, sample books, and FTS dictionary off main thread
        appScope.launch {
            BundledThemes.all
            container.libraryRepository.initializeDefaults()
            container.annotationRepository.ensureDefaultTemplates()
            container.dictionaryRepository.ensureBundledDictionary()
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.18)
                    .strongReferencesEnabled(true)
                    .weakReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_image_cache"))
                    .maxSizeBytes(48L * 1024L * 1024L)
                    .build()
            }
            .allowHardware(true)
            .allowRgb565(true)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .bitmapFactoryMaxParallelism(3)
            .crossfade(160)
            .respectCacheHeaders(false)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Coil.imageLoader(this).memoryCache?.trimMemory(level)
        PdfPageEngine.trimMemory(level)
        ReaderDocumentCache.trimMemory(level)
    }
}
