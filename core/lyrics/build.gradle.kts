plugins {
  alias(libs.plugins.kotlin.jvm)
}

dependencies {
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.jetbrains.kotlin:kotlin-test:2.3.20")
}

tasks.test { useJUnit() }
