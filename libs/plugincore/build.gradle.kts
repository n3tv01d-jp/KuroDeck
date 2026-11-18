plugins {
    alias(libs.plugins.android.library)

}

val root = rootDir.path

android {
    namespace = "com.android.systemui.plugins"
    compileSdk = 37
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        named("main") {
            java.setSrcDirs(listOf("$root/third_party/plugin_core/src", "$root/third_party/plugin_core/annotations/src", "$root/launcher3/src_plugins"))
            kotlin.setSrcDirs(listOf("$root/third_party/plugin_core/src", "$root/third_party/plugin_core/annotations/src", "$root/launcher3/src_plugins"))
            res.setSrcDirs(listOf<String>())
            manifest.srcFile("$root/stubs/src/main/AndroidManifest.xml")
        }
    }
    lint { abortOnError = false }
}

dependencies {
    api(libs.androidx.annotation)
    api(project(":stubs"))
}
