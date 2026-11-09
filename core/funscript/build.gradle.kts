plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlinx.serialization)
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.jetbrains.kotlin:kotlin-test:2.3.20")
}

tasks.test { useJUnit() }
