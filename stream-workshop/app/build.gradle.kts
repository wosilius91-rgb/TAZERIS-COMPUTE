plugins {
    id("com.android.application")
}
android {
    namespace = "com.tazeris.streamworkshop"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.tazeris.streamworkshop"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release { isMinifyEnabled = false }
        debug { isMinifyEnabled = false }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}
dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
        testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
