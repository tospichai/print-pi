plugins {
    id("com.android.application")
}

android {
    namespace = "com.maepranam.networkprinter"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.maepranam.networkprinter"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "android.app.InstrumentationTestRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.pusher:pusher-java-client:2.4.4")
    implementation("com.google.code.gson:gson:2.13.2")
    testImplementation("junit:junit:4.13.2")
}
