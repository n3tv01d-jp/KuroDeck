// KuroDeck: GrapheneOS Launcher3 (non-Quickstep variant) built standalone, plus the
// Hearth-styled HUD layer in /kurodeck.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
}

val l3 = rootDir.resolve("launcher3")
val kd = rootDir.resolve("kurodeck")

android {
    // Launcher3 code and resources live in com.android.launcher3; the installed package uses
    // its own id so it can sit beside the stock GrapheneOS launcher.
    namespace = "com.android.launcher3"
    // 37.2 adds the App Lock APIs Launcher3 uses (behind android.security.app_lock_apis).
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "app.kurodeck.launcher"
        minSdk = 31
        targetSdk = 37
        versionCode = 3
        versionName = "0.3.0"

        // Matches tools/buildconfig.sh in Launcher3.
        buildConfigField("boolean", "IS_STUDIO_BUILD", "false")
        buildConfigField("boolean", "QSB_ON_FIRST_SCREEN", "true")
        buildConfigField("boolean", "IS_DEBUG_DEVICE", "false")
        buildConfigField("boolean", "WIDGETS_ENABLED", "true")
        buildConfigField("boolean", "NOTIFICATION_DOTS_ENABLED", "true")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // Launcher3 ships with proguard disabled in AOSP; keep the same behaviour.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    sourceSets {
        named("main") {
            val src = listOf(
                l3.resolve("src"),
                l3.resolve("src_no_quickstep"),
                l3.resolve("dagger/src"),
                l3.resolve("modules/concurrent/src"),
                l3.resolve("shared/src"),
                kd.resolve("src"),
            )
            java.setSrcDirs(src)
            kotlin.setSrcDirs(src)
            res.setSrcDirs(listOf(l3.resolve("res")))
        }
        // Build-type source sets override `main`, which is how KuroDeck's resources replace
        // Launcher3's without touching the vendored tree.
        named("debug") { res.srcDir(kd.resolve("res")) }
        named("release") { res.srcDir(kd.resolve("res")) }
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/AL2.0", "META-INF/LGPL2.1")
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjvm-default=all", "-Xannotation-default-target=param-property")
    }
}

dependencies {
    implementation(project(":flags"))
    implementation(project(":stubs"))
    implementation(project(":libs:iconloader"))
    implementation(project(":libs:animation"))
    implementation(project(":libs:msdl"))
    implementation(project(":libs:usertype"))
    implementation(project(":libs:dynamiccolors"))
    implementation(project(":libs:plugincore"))
    implementation(project(":libs:widgetpicker"))
    implementation(project(":libs:appfunctions"))
    implementation(project(":libs:protos"))
    implementation(project(":libs:stockres"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.slice.view)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.window)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.common.java8)
    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.guava)
    implementation(libs.graphics.shapes)

    implementation(libs.dagger)
    implementation(libs.javax.inject)
    implementation(libs.jsr305)
    ksp(libs.dagger.compiler)
}
