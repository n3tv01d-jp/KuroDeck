// Launcher3's Compose widget picker (packages/apps/Launcher3/modules/widgetpicker).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val wp = rootDir.resolve("launcher3/modules/widgetpicker")

android {
    namespace = "com.android.launcher3.widgetpicker"
    compileSdk = 37
    defaultConfig { minSdk = 31 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        named("main") {
            java.setSrcDirs(listOf(wp.resolve("src")))
            kotlin.setSrcDirs(listOf(wp.resolve("src")))
            res.setSrcDirs(listOf(wp.resolve("res")))
            manifest.srcFile(wp.resolve("AndroidManifest.xml"))
        }
    }
    lint { abortOnError = false }
}

dependencies {
    implementation(platform(libs.compose.bom))
    api(libs.androidx.core.ktx)
    api(libs.dagger)
    api(libs.javax.inject)
    ksp(libs.dagger.compiler)
    api(libs.coroutines.android)
    api(libs.compose.runtime)
    api(libs.compose.ui)
    api(libs.compose.ui.util)
    api(libs.compose.foundation)
    api(libs.compose.foundation.layout)
    api(libs.compose.material3)
    api(libs.compose.material.icons.extended)
    api(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    api(libs.androidx.activity.compose)
    api(libs.androidx.lifecycle.viewmodel.compose)
    api(libs.androidx.window)
}
