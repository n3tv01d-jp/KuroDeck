// Small stand-ins for SystemUI / WM-Shell classes Launcher3 links against but which only
// exist inside the platform build.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
}
android {
    namespace = "kurodeck.stubs"
    compileSdk = 37
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.annotation)
}
