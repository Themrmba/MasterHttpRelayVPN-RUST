import org.gradle.api.tasks.Exec

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.therealaleph.mhrv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.therealaleph.mhrv"
        minSdk = 24
        targetSdk = 34
        versionCode = 158
        versionName = "1.8.1"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = "mhrv-rs-release"
            keyAlias = "mhrv-rs"
            keyPassword = "mhrv-rs-release"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].jniLibs.srcDirs("src/main/jniLibs")

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Compose UI.
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Ripple indication (needed for rememberRipple)
    implementation("androidx.compose.material:material-ripple")

    // QR code generation + scanning
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Rust cross-compile tasks
val rustCrateDir = rootProject.projectDir.parentFile
val jniLibsDir = file("src/main/jniLibs")

fun normalizeTun2proxySo() {
    val jniLibsRoot = file("src/main/jniLibs")
    if (!jniLibsRoot.isDirectory) return
    jniLibsRoot.listFiles()?.filter { it.isDirectory }?.forEach { abiDir ->
        val hashed = abiDir.listFiles { f -> f.name.matches(Regex("libtun2proxy-[0-9a-f]+\\.so")) }
            ?: emptyArray()
        val newest = hashed.maxByOrNull { it.lastModified() }
        if (newest != null) {
            val target = abiDir.resolve("libtun2proxy.so")
            if (target.exists()) target.delete()
            newest.copyTo(target, overwrite = true)
        }
        hashed.forEach { it.delete() }
    }
}

val androidAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

tasks.register<Exec>("cargoBuildDebug") {
    group = "build"
    description = "Cross-compile mhrv_rs for all ABIs (release — same as cargoBuildRelease)"
    workingDir = rustCrateDir
    commandLine(buildList<String> {
        add("cargo"); add("ndk")
        androidAbis.forEach { add("-t"); add(it) }
        add("-o"); add(jniLibsDir.absolutePath)
        add("build"); add("--release")
    })
    doLast { normalizeTun2proxySo() }
}

tasks.register<Exec>("cargoBuildRelease") {
    group = "build"
    description = "Cross-compile mhrv_rs for all ABIs (release)"
    workingDir = rustCrateDir
    commandLine(buildList<String> {
        add("cargo"); add("ndk")
        androidAbis.forEach { add("-t"); add(it) }
        add("-o"); add(jniLibsDir.absolutePath)
        add("build"); add("--release")
    })
    doLast { normalizeTun2proxySo() }
}

tasks.configureEach {
    when (name) {
        "mergeDebugJniLibFolders" -> dependsOn("cargoBuildDebug")
        "mergeReleaseJniLibFolders" -> dependsOn("cargoBuildRelease")
    }
}
