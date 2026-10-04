plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.bushorat.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bushorat.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation("androidx.activity:activity-result:1.7.2")
    implementation("androidx.core:core:1.12.0")
    implementation(libs.constraintlayout)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.storage)
    implementation(libs.play.services.auth)
    implementation (libs.glide)
    implementation(libs.media3.exoplayer)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.media3.ui)
    implementation(libs.swiperefreshlayout)
    annotationProcessor (libs.compiler)
    testImplementation(libs.junit)
    implementation (libs.work.runtime)
    implementation (libs.exoplayer)
    implementation (libs.exoplayer.ui)
//    implementation("com.github.smarteist:Android-Image-Slider:1.3.9")
//    implementation ("com.google.android.exoplayer:extension-okhttp:2.19.1") // For caching
//    implementation ("com.squareup.okhttp3:okhttp:4.9.3")
//    implementation("com.android.volley:volley:1.2.1")
//    implementation ("androidx.work:work-runtime:2.7.1")
//    implementation("com.github.hajiyevelnur92:intentanimation:1.0")

    implementation ("com.airbnb.android:lottie:4.1.0")
    implementation ("com.mikhaellopez:circularimageview:4.3.0")
    implementation("io.github.rupinderjeet:kprogresshud:1.0.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    
    // Coroutines for Firebase async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")


    implementation ("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.16.0")

    // CameraX dependencies
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}