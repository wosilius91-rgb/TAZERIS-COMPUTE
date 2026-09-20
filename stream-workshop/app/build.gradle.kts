plugins {
    id("com.android.application")
}
android {
    namespace = "com.tazeris.streamworkshop"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.tazeris.streamworkshop"
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
    implementation("com.github.TeamNewPipe:NewPipeExtractor:ab984a8e3bcd0bc6d4b5f90860815f7b10476541")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
