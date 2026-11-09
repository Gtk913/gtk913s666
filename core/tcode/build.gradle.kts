plugins {
  alias(libs.plugins.android.library)
}

// AGP 9.0 起内置 Kotlin 支持并默认开启，Android 模块不再需要、也不允许
// 声明额外的 Kotlin Android 插件；本模块的 .kt 源码由 AGP 直接编译。
// 编译目标跟随 compileOptions 的 JDK 17。

android {
  namespace = "com.tcodeplayer.core.tcode"
  compileSdk = 36

  defaultConfig {
    minSdk = 26
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
  testImplementation("junit:junit:4.13.2")
}
