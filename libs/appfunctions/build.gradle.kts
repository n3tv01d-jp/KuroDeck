// Launcher3 workspace AppFunctions (packages/apps/Launcher3/modules/appfunctions).
// Compiled against KuroDeck's inert API shims in :stubs (see AppFunctionShims.kt).
plugins {
    alias(libs.plugins.android.library)
}

val af = rootDir.resolve("launcher3/modules/appfunctions")

android {
    namespace = "com.android.launcher3.appfunctions"
    compileSdk = 37
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        named("main") {
            java.setSrcDirs(listOf(af.resolve("src")))
            kotlin.setSrcDirs(listOf(af.resolve("src")))
            manifest.srcFile(af.resolve("AndroidManifest.xml"))
        }
    }
    lint { abortOnError = false }
}

dependencies {
    api(libs.androidx.annotation)
    api(libs.androidx.appcompat)
    api(project(":stubs"))
}
