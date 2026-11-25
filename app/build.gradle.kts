import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "app.mochireader.reader"
    minSdk = 24
    targetSdk = 36

    val versionPropsFile = rootProject.file("version.properties")
    val propsMajor = if (versionPropsFile.exists()) {
      versionPropsFile.readLines().firstOrNull { it.startsWith("VERSION_MAJOR=") }?.substringAfter("=")?.trim()?.toIntOrNull() ?: 1
    } else 1
    val propsMinor = if (versionPropsFile.exists()) {
      versionPropsFile.readLines().firstOrNull { it.startsWith("VERSION_MINOR=") }?.substringAfter("=")?.trim()?.toIntOrNull() ?: 1
    } else 1
    val propsPatch = if (versionPropsFile.exists()) {
      versionPropsFile.readLines().firstOrNull { it.startsWith("VERSION_PATCH_BASE=") }?.substringAfter("=")?.trim()?.toIntOrNull() ?: 0
    } else 0

    versionCode = System.getenv("MOCHI_VERSION_CODE")?.toIntOrNull()
      ?: project.findProperty("mochiVersionCode")?.toString()?.toIntOrNull()
      ?: (propsMajor * 100000 + propsMinor * 10000 + propsPatch)
    versionName = System.getenv("MOCHI_VERSION_NAME")?.takeIf { it.isNotBlank() }
      ?: project.findProperty("mochiVersionName")?.toString()?.takeIf { it.isNotBlank() }
      ?: "$propsMajor.$propsMinor.$propsPatch"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        it.systemProperty(
          "robolectric.dependency.repo.url",
          "https://maven-central.storage-download.googleapis.com/maven2"
        )
      }
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Network access is limited strictly to optional Project Gutenberg catalog & cover fetching.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose) // Edge-to-edge activity host for Compose
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core) // Core Material symbols
  implementation(libs.androidx.compose.material.icons.extended) // Extended reader/library icons
  implementation(libs.androidx.compose.material3) // Material 3 UI components and theming
  implementation(libs.androidx.compose.ui) // Jetpack Compose UI core
  implementation(libs.androidx.compose.ui.graphics) // RenderEffect, shaders, and Canvas graphics
  implementation(libs.androidx.compose.ui.tooling.preview) // Compose preview support
  implementation(libs.androidx.core.ktx) // AndroidX Kotlin extensions
  implementation(libs.androidx.datastore.preferences) // Local user preferences storage
  implementation(libs.androidx.documentfile) // SAF directory tree traversal
  implementation(libs.androidx.lifecycle.runtime.compose) // Lifecycle-aware StateFlow collection
  implementation(libs.androidx.lifecycle.runtime.ktx) // Lifecycle scopes
  implementation(libs.androidx.lifecycle.viewmodel.compose) // ViewModel integration for Compose
  implementation(libs.androidx.navigation.compose) // Type-safe navigation between screens
  implementation(libs.androidx.room.ktx) // Coroutine & Flow extensions for SQLite
  implementation(libs.androidx.room.runtime) // Offline SQLite database for library and themes
  implementation(libs.androidx.work.runtime.ktx) // Background SAF folder scanning
  implementation(libs.coil.compose) // Off-main-thread cached cover image loading
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android) // Main-safe async execution
  implementation(libs.kotlinx.coroutines.core) // Coroutine primitives and Flows
  // implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin) // JSON parsing for themes and Gutendex API
  implementation(libs.okhttp) // HTTP client for Project Gutenberg catalog & ebook downloads
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
