// 纯 Kotlin / JVM 模块：不得依赖 mpv、mpvEx、Android UI（方案第 3 节边界规则）
plugins {
  alias(libs.plugins.kotlin.jvm)
}

dependencies {
  // StateFlow 来自 kotlinx-coroutines-core（纯 Kotlin/JVM，Stable）
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.jetbrains.kotlin:kotlin-test:2.3.20")
}

tasks.test { useJUnit() }
