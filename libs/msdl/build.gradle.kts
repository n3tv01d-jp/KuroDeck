plugins {
    alias(libs.plugins.android.library)

}

val root = rootDir.path

android {
    namespace = "com.google.android.msdl"
    compileSdk = 37
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        named("main") {
            java.setSrcDirs(listOf("$root/third_party/systemui/msdllib/src"))
            kotlin.setSrcDirs(listOf("$root/third_party/systemui/msdllib/src"))
            res.setSrcDirs(listOf<String>())
            manifest.srcFile("$root/third_party/systemui/msdllib/AndroidManifest.xml")
        }
    }
    lint { abortOnError = false }
}

dependencies {
    api(libs.coroutines.android)
    api(libs.androidx.annotation)
}
