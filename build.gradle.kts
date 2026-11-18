plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.protobuf) apply false
}

/** Root of the vendored GrapheneOS Launcher3 tree. */
extra["L3"] = rootDir.resolve("launcher3").path
/** Root of the vendored frameworks/libs/systemui libraries. */
extra["SYSUI"] = rootDir.resolve("third_party/systemui").path
