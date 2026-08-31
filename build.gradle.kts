// Top-level build file. Plugin versions live in gradle/libs.versions.toml;
// nothing is applied here — :app decides what it needs.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
