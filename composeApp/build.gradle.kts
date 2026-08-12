import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmp)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    // AGP 9 KMP library target — replaces the old androidTarget() + android {} pair,
    // which AGP 9 rejects when combined with org.jetbrains.kotlin.multiplatform.
    androidLibrary {
        // Distinct from the app shell's namespace (com.radar.news) to avoid manifest
        // merger namespace collisions. Kotlin packages stay com.radar.news.*.
        namespace = "com.radar.news.shared"
        compileSdk = 37
        minSdk = 24
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // iOS targets are only configurable on macOS hosts (Kotlin/Native rule). On Windows
    // (this dev box) they are skipped so the Android target can still be compiled locally;
    // on macOS / Codemagic they are created as usual.
    val hostOs = System.getProperty("os.name")
    val isMacOs = hostOs == "Mac OS X"
    if (isMacOs) {
        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64(),
        ).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform (same API surface as the Android app's Compose)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.materialIconsExtended)

            // Ported data layer — pure Kotlin
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.okhttp)
            implementation(libs.ksoup)
            implementation(libs.coil)
            implementation(libs.coil.network.okhttp)

            // Storage (Room supports KMP since 2.7)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.paging)
            implementation(libs.androidx.sqlite.bundled)

            // DI (Hilt is Android-only; Koin is the KMP replacement)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.browser)
            implementation(libs.koin.android)
        }
        iosMain.dependencies {
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Room KSP processing (KMP source set)
    add("kspCommonMainMetadata", libs.androidx.room.compiler)
}
