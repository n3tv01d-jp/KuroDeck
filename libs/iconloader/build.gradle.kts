plugins {
    alias(libs.plugins.android.library)

}

val root = rootDir.path

android {
    namespace = "com.android.launcher3.icons"
    compileSdk = 37
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        named("main") {
            java.setSrcDirs(listOf("$root/third_party/systemui/iconloaderlib/src"))
            kotlin.setSrcDirs(listOf("$root/third_party/systemui/iconloaderlib/src"))
            res.setSrcDirs(listOf("$root/third_party/systemui/iconloaderlib/res"))
            manifest.srcFile("$root/third_party/systemui/iconloaderlib/AndroidManifest.xml")
        }
    }
    lint { abortOnError = false }
}

dependencies {
    api(libs.androidx.core)
    api(project(":flags"))
    api(project(":libs:usertype"))
}
