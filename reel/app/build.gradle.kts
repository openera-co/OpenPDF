import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val signing = Properties().apply {
    val f = rootProject.file("signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.reel.editor"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.reel.editor"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "1.6.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Where the app looks for updates (an update.json from tools/update/make_update.py).
        // Set with -Preel.updateUrl=... or in gradle.properties; users can also enter one in the app.
        buildConfigField("String", "UPDATE_URL", "\"${(project.findProperty("reel.updateUrl") as String?) ?: ""}\"")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3", "-fvisibility=hidden")
                arguments += listOf("-DANDROID_STL=c++_static", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }
    }

    signingConfigs {
        create("release") {
            if (signing.getProperty("storeFile") != null) {
                storeFile = rootProject.file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Crash and performance reports go to Sentry from release builds only.
            buildConfigField("String", "SENTRY_DSN", "\"https://96eecaa27224311369a78411dd7e35a1@o4512164490444800.ingest.de.sentry.io/4512427704189008\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signing.getProperty("storeFile") != null) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            buildConfigField("String", "SENTRY_DSN", "\"\"")
            applicationIdSuffix = ".debug"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs { useLegacyPackaging = false }
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    // Models are read in place from the APK, so they must not be compressed.
    androidResources { noCompress += listOf("bin", "tflite") }
}

// The speech model (264 MB) is not kept in git: fetched once and checked before every build.
val speechModel = layout.projectDirectory.file("src/main/assets/models/speech.bin").asFile
val speechModelUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q8_0.bin"
val speechModelSha256 = "49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f"
val speechModelBytes = 264_464_607L

fun sha256Of(f: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    f.inputStream().use { input ->
        val buf = ByteArray(1 shl 20)
        while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

val fetchSpeechModel by tasks.registering {
    outputs.file(speechModel)
    outputs.upToDateWhen { speechModel.length() == speechModelBytes }
    doLast {
        if (speechModel.length() == speechModelBytes && sha256Of(speechModel) == speechModelSha256) return@doLast
        speechModel.parentFile.mkdirs()
        val tmp = File(speechModel.path + ".part")
        URI(speechModelUrl).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        check(sha256Of(tmp) == speechModelSha256) { "speech model checksum mismatch" }
        check(tmp.renameTo(speechModel)) { "could not move speech model into place" }
    }
}
tasks.named("preBuild") { dependsOn(fetchSpeechModel) }

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(listOf("-opt-in=androidx.media3.common.util.UnstableApi", "-opt-in=androidx.media3.common.util.ExperimentalApi", "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api", "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi", "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi"))
    }
}

dependencies {
    val media3 = "1.11.1"
    implementation("androidx.media3:media3-transformer:$media3")
    implementation("androidx.media3:media3-effect:$media3")
    implementation("androidx.media3:media3-common:$media3")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-muxer:$media3")

    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    // On-device person segmentation for background cutout (16 KB page aligned).
    implementation("com.google.ai.edge.litert:litert:1.4.2")
    // Crash, ANR and native crash reporting.
    implementation("io.sentry:sentry-android:8.63.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.2.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
}
