import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.net.URI
import java.security.DigestInputStream
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
}

android {
    namespace = "com.yassernull.modulesbox"
    compileSdk = 34
    buildToolsVersion = "35.0.0"
    ndkVersion = "29.0.14206865"
    defaultConfig {
        applicationId = "com.yassernull.modulesbox"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

val buildJni by tasks.registering(Exec::class) {
    val ndkDir = android.ndkDirectory
    val ndkBuild = File(ndkDir, "ndk-build")
    val ndkObjDir = layout.buildDirectory.dir("ndk/obj").get().asFile
    val ndkLibsDir = layout.buildDirectory.dir("ndk/libs").get().asFile
    workingDir = File(projectDir, "src/main/jni")
    commandLine(
        ndkBuild.absolutePath,
        "NDK_PROJECT_PATH=${projectDir}/src/main/jni",
        "APP_BUILD_SCRIPT=${projectDir}/src/main/jni/Android.mk",
        "NDK_OUT=${ndkObjDir}",
        "NDK_LIBS_OUT=${ndkLibsDir}"
    )
}

abstract class CopyBinariesTask @javax.inject.Inject constructor() : DefaultTask() {

    @get:InputDirectory
    abstract val buildLibsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outDir: DirectoryProperty

    @TaskAction
    fun copyFiles() {
        val abis = listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        val binaries = listOf("rish", "init-host")

        abis.forEach { abi ->
            binaries.forEach { bin ->
                val source = File(buildLibsDir.get().asFile, "$abi/$bin")
                val target = File(outDir.get().asFile, "$abi/lib$bin.so")

                if (source.exists()) {
                    target.parentFile.mkdirs()
                    source.copyTo(target, overwrite = true)
                }
            }
        }
    }
}

val copyBinaries by tasks.registering(CopyBinariesTask::class) {
    dependsOn(buildJni)
    buildLibsDir.set(layout.buildDirectory.dir("ndk/libs"))
    outDir.set(File(projectDir, "src/main/jniLibs"))
}

tasks.named("preBuild") {
    dependsOn(copyBinaries)
}


abstract class DownloadPrebuiltTask @javax.inject.Inject constructor() : DefaultTask() {
    @get:OutputDirectory
    abstract val jniLibsDir: DirectoryProperty

    private fun downloadFile(localPath: String, remoteUrl: String, expectedChecksum: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        val file = File(jniLibsDir.get().asFile, localPath)

        if (file.exists()) {
            val buffer = ByteArray(8192)
            val input = FileInputStream(file)
            while (true) {
                val readBytes = input.read(buffer)
                if (readBytes < 0) break
                digest.update(buffer, 0, readBytes)
            }
            var checksum = BigInteger(1, digest.digest()).toString(16)
            while (checksum.length < 64) { checksum = "0$checksum" }
            if (checksum == expectedChecksum) {
                return
            } else {
                logger.warn("Deleting old local file with wrong hash: $localPath: expected: $expectedChecksum, actual: $checksum")
                file.delete()
            }
        }

        logger.quiet("Downloading $remoteUrl ...")

        file.parentFile.mkdirs()
        val out = BufferedOutputStream(FileOutputStream(file))

        val connection = URI(remoteUrl).toURL().openConnection()
        val digestStream = DigestInputStream(connection.inputStream, digest)
        digestStream.transferTo(out)
        out.close()

        var checksum = BigInteger(1, digest.digest()).toString(16)
        while (checksum.length < 64) { checksum = "0$checksum" }
        if (checksum != expectedChecksum) {
            file.delete()
            throw GradleException("Wrong checksum for $remoteUrl:\n Expected: $expectedChecksum\n Actual:   $checksum")
        }
    }

    @TaskAction
    fun download() {
        val prootTag = "proot-2025.01.15-r2"
        val prootVersion = "5.1.107-66"
        var prootUrl = "https://github.com/termux-play-store/termux-packages/releases/download/${prootTag}/libproot-loader-ARCH-${prootVersion}.so"

        downloadFile("armeabi-v7a/libproot-loader.so", prootUrl.replace("ARCH", "arm"), "eb1d64e9ef875039534ce7a8eeffa61bbc4c0ae5722cb48c9112816b43646a3e")
        downloadFile("arm64-v8a/libproot-loader.so", prootUrl.replace("ARCH", "aarch64"), "8814b72f760cd26afe5350a1468cabb6622b4871064947733fcd9cd06f1c8cb8")
        downloadFile("x86_64/libproot-loader.so", prootUrl.replace("ARCH", "x86_64"), "1a52cc9cc5fdecbf4235659ffeac8c51e4fefd7c75cc205f52d4884a3a0a0ba1")
        prootUrl = "https://github.com/termux-play-store/termux-packages/releases/download/${prootTag}/libproot-loader32-ARCH-${prootVersion}.so"
        downloadFile("arm64-v8a/libproot-loader32.so", prootUrl.replace("ARCH", "aarch64"), "ff56a5e3a37104f6778420d912e3edf31395c15d1528d28f0eb7d13a64481b99")
        downloadFile("x86_64/libproot-loader32.so", prootUrl.replace("ARCH", "x86_64"), "5460a597e473f57f0d33405891e35ca24709173ca0a38805d395e3544ab8b1b4")
    }
}

val downloadPrebuilt by tasks.registering(DownloadPrebuiltTask::class) {
    jniLibsDir.set(File(projectDir, "src/main/jniLibs"))
}

tasks.named("preBuild") {
    dependsOn(downloadPrebuilt)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-ripple")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.30.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("com.google.accompanist:accompanist-swiperefresh:0.32.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.30.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("io.ktor:ktor-client-core:2.3.6")
    implementation("io.ktor:ktor-client-android:2.3.6")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.6")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.6")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("org.slf4j:slf4j-nop:2.0.12")
    implementation("org.snakeyaml:snakeyaml-engine:2.7")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.32.0")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation("com.github.topjohnwu.libsu:service:6.0.0")
    implementation("com.github.topjohnwu.libsu:nio:6.0.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-svg:2.5.0")
    implementation(project(":terminal-view"))
    implementation(project(":terminal-emulator"))
}
