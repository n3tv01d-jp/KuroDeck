plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
}

val root = rootDir.path

android {
    namespace = "com.android.users"
    compileSdk = 37
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        named("main") {
            java.setSrcDirs(listOf("$root/third_party/systemui/usertypelib/src"))
            kotlin.setSrcDirs(listOf("$root/third_party/systemui/usertypelib/src"))
            res.setSrcDirs(listOf<String>())
            manifest.srcFile("$root/third_party/systemui/usertypelib/AndroidManifest.xml")
        }
    }
    lint { abortOnError = false }
}

dependencies {

}
