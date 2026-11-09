import com.android.build.api.variant.FilterConfiguration
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
}

android {
  namespace = "app.marlboroadvance.mpvex"
  compileSdk = 36

  defaultConfig {
    // Keep the Kotlin namespace aligned with upstream to minimize merge conflicts,
    // while using a distinct application ID so this fork can be installed alongside it.
    applicationId = "io.github.yaodao0yaodao.mpvex"
    minSdk = 26
    targetSdk = 36
    versionCode = 13010
    versionName = "1.3.1"

    vectorDrawables {
      useSupportLibrary = true
    }

    buildConfigField("String", "GIT_SHA", "\"${getCommitSha()}\"")
    buildConfigField("int", "GIT_COUNT", getCommitCount())
  }

  flavorDimensions += "distribution"

  productFlavors {
    create("standard") {
      dimension = "distribution"
      buildConfigField("boolean", "ENABLE_UPDATE_FEATURE", "true")
      buildConfigField("boolean", "SCOPED_STORAGE_ONLY", "false")
    }

    create("playstore") {
      dimension = "distribution"
      versionNameSuffix = "-playstore"
      buildConfigField("boolean", "ENABLE_UPDATE_FEATURE", "false")
      buildConfigField("boolean", "SCOPED_STORAGE_ONLY", "true")
    }

    create("fdroid") {
      dimension = "distribution"
      versionNameSuffix = "-fdroid"
      buildConfigField("boolean", "ENABLE_UPDATE_FEATURE", "false")
      buildConfigField("boolean", "SCOPED_STORAGE_ONLY", "false")

    }
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }

  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a")
      isUniversalApk = false
    }
  }

  buildTypes {
    named("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      ndk {
        debugSymbolLevel = "none"
      }
    }

    create("preview") {
      initWith(getByName("release"))
      signingConfig = null
      applicationIdSuffix = ".preview"
      versionNameSuffix = "-${getCommitCount()}"
    }

    named("debug") {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-${getCommitCount()}"
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    viewBinding = true
    buildConfig = true
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "META-INF/DEPENDENCIES"
      excludes += "META-INF/LICENSE*"
      excludes += "META-INF/NOTICE*"
      excludes += "META-INF/*.kotlin_module"
      excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    }
    jniLibs {
      useLegacyPackaging = true
    }
  }

  @Suppress("UnstableApiUsage")
  androidResources {
    generateLocaleConfig = true
  }
}

androidComponents {
  val abiCodes = mapOf(
    "armeabi-v7a" to 1,
    "arm64-v8a" to 2,
    "x86" to 3,
    "x86_64" to 4
  )

  onVariants { variant ->
    variant.outputs.forEach { output ->
      val abi = output.filters
        .find { it.filterType == FilterConfiguration.FilterType.ABI }
        ?.identifier

      output.versionCode.set(
        (output.versionCode.orNull ?: 0) * 10 + (abiCodes[abi] ?: 0)
      )
    }
  }
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll(
      "-Xwhen-guards",
      "-Xcontext-parameters",
      "-Xannotation-default-target=param-property",
      "-opt-in=com.google.accompanist.permissions.ExperimentalPermissionsApi",
      "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
    )
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

composeCompiler {
  includeSourceInformation = true
}

room {
  schemaDirectory("$projectDir/schemas")
}

dependencies {
  // ---- TCode Player（策划案第 3–7 步）----
  implementation(project(":core:axis"))
  implementation(project(":core:funscript"))
  implementation(project(":core:lyrics"))
  implementation(project(":core:tcode"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.material3.android)
  implementation("com.google.android.material:material:1.13.0")
  implementation(libs.androidx.compose.material)
  implementation(libs.androidx.ui.tooling.preview)
  debugImplementation(libs.androidx.ui.tooling)
  implementation(libs.bundles.compose.navigation3)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.compose.constraintlayout)
  implementation("androidx.preference:preference-ktx:1.2.1")
  implementation("androidx.constraintlayout:constraintlayout:2.2.0")
  implementation(libs.androidx.material3.icons.extended)
  implementation(libs.androidx.compose.animation.graphics)
  implementation(libs.mediasession)
  implementation(libs.androidx.documentfile)
  implementation(libs.saveable)

  implementation(platform(libs.koin.bom))
  implementation(libs.bundles.koin)

  implementation(libs.seeker)
  implementation(libs.compose.prefs)

  implementation(libs.accompanist.permissions)

  implementation(libs.room.runtime)
  ksp(libs.room.compiler)
  implementation(libs.room.ktx)

  implementation(libs.kotlinx.immutable.collections)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.okhttp)

  implementation(libs.truetype.parser)
  implementation(libs.fsaf)
  implementation(libs.mediainfo.lib)
  implementation(files("libs/mpv-android-lib-v0.0.1.aar"))

  // Network protocol libraries
  // 第十二轮：USB 有线串口（CH340 / CP210x / FTDI），稳定 release tag
  implementation("com.github.mik3y:usb-serial-for-android:3.9.0")
  implementation(libs.smbj)
  implementation(libs.commons.net)
  implementation(libs.sardine.android) {
    exclude(group = "xpp3", module = "xpp3")
  }
  implementation(libs.nanohttpd)
  implementation(libs.lazycolumnscrollbar)
  implementation(libs.reorderable)

  testImplementation("junit:junit:4.13.2")
}

/* ---------------- Git helpers ----------------
 * 源码包以 zip 解压分发，不带 .git 目录。
 * 旧实现把 git 的 stderr 合并进 stdout，
 * 且不检查退出码，导致 "fatal: not a git repository ..." 被当成
 * GIT_COUNT 写进 BuildConfig.java，生成非法 Java 源码。
 * 现在：检查退出码 + 校验输出格式，任何失败都回退到安全常量。
 */

fun getCommitCount(): String =
  gitOutput("rev-list", "--count", "HEAD")
    ?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    ?: "0"

fun getCommitSha(): String =
  gitOutput("rev-parse", "--short", "HEAD")
    ?.takeIf { it.matches(Regex("[0-9a-fA-F]{4,40}")) }
    ?: "unknown"

fun gitOutput(vararg args: String): String? =
  try {
    val process = ProcessBuilder(listOf("git") + args)
      .redirectErrorStream(false)
      .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    val exit = process.waitFor()
    if (exit == 0) output.ifEmpty { null } else null
  } catch (e: Exception) {
    null
  }
