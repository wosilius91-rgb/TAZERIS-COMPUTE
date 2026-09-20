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
}
dependencies {
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.5")
}
