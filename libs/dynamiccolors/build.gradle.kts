plugins {
    alias(libs.plugins.android.library)

}

val root = rootDir.path

android {
    namespace = "com.android.systemui.dynamiccolors"
    compileSdk = 37
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        named("main") {
            java.setSrcDirs(listOf<String>())
            kotlin.setSrcDirs(listOf<String>())
            res.setSrcDirs(listOf("$root/third_party/systemui/dynamiccolors/res"))
            manifest.srcFile("$root/third_party/systemui/dynamiccolors/AndroidManifest.xml")
        }
    }
    lint { abortOnError = false }
}

dependencies {

}
