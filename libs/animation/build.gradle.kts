plugins {
    alias(libs.plugins.android.library)

}

val root = rootDir.path

android {
    namespace = "com.android.app.animation"
    compileSdk = 37
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        named("main") {
            java.setSrcDirs(listOf("$root/third_party/systemui/animationlib/src"))
            kotlin.setSrcDirs(listOf("$root/third_party/systemui/animationlib/src"))
            res.setSrcDirs(listOf("$root/third_party/systemui/animationlib/res"))
            manifest.srcFile("$root/third_party/systemui/animationlib/AndroidManifest.xml")
        }
    }
    lint { abortOnError = false }
}

dependencies {
    api(libs.androidx.core.animation)
    api(libs.androidx.core.ktx)
    api(libs.androidx.annotation)
}
