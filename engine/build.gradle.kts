import javax.inject.Inject
import org.gradle.process.ExecOperations

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.halilkhrmn.dpimech.engine"
    compileSdk = 37
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
}

/**
 * Builds the engines with ndk-build (native/Android.mk) and lays them out as jniLibs.
 * ciadpi is an executable; it is renamed to libciadpi.so so the installer puts it into
 * nativeLibraryDir, the one app directory Android lets apps execute from.
 */
abstract class NdkBuildEngines : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nativeDir: DirectoryProperty

    @get:Input
    abstract val ndkDir: Property<String>

    @get:Internal
    abstract val workDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val exec: ExecOperations

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun build() {
        val native = nativeDir.get().asFile
        val work = workDir.get().asFile
        val libs = File(work, "libs")
        exec.exec {
            workingDir = native
            commandLine(
                File(ndkDir.get(), "ndk-build").path,
                "-j${Runtime.getRuntime().availableProcessors()}",
                "NDK_PROJECT_PATH=.",
                "APP_BUILD_SCRIPT=Android.mk",
                "NDK_APPLICATION_MK=Application.mk",
                "NDK_OUT=${File(work, "obj").path}",
                "NDK_LIBS_OUT=${libs.path}",
            )
        }
        fs.sync {
            from(libs)
            into(outputDir)
            include("*/libhev-socks5-tunnel.so", "*/ciadpi")
            rename("ciadpi", "libciadpi.so")
        }
    }
}

val ndkBuildEngines = tasks.register<NdkBuildEngines>("ndkBuildEngines") {
    nativeDir.set(rootProject.layout.projectDirectory.dir("native"))
    ndkDir.set(androidComponents.sdkComponents.ndkDirectory.map { it.asFile.path })
    workDir.set(layout.buildDirectory.dir("ndk"))
    outputDir.set(layout.buildDirectory.dir("ndk/jniLibs"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(ndkBuildEngines, NdkBuildEngines::outputDir)
    }
}
