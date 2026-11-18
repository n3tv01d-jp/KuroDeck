// GrapheneOS's `res-stock` overlay. In Soong it sits after `res` in resource_dirs (lower
// priority); as a library it is likewise overridden by the app's resources.
plugins { alias(libs.plugins.android.library) }
android {
    namespace = "com.android.launcher3.stockres"
    compileSdk = 37
    defaultConfig { minSdk = 31 }
    sourceSets {
        named("main") {
            res.setSrcDirs(listOf(rootDir.resolve("launcher3/res-stock")))
            manifest.srcFile(rootDir.resolve("stubs/src/main/AndroidManifest.xml"))
        }
    }
}
