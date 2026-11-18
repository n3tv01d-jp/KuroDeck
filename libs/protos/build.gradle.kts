import com.google.protobuf.gradle.proto

// Launcher logging protos (launcher_atom etc.), compiled as protobuf-lite.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.protobuf)
}

val l3 = rootDir.resolve("launcher3")

android {
    namespace = "com.android.launcher3.logger"
    compileSdk = 37
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        named("main") {
            proto {
                srcDir(l3.resolve("protos"))
                srcDir(l3.resolve("protos_overrides"))
            }
        }
    }
}

protobuf {
    protoc { artifact = libs.protoc.get().toString() }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins { create("java") { option("lite") } }
        }
    }
}

dependencies {
    api(libs.protobuf.javalite)
}
