plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "xtr.compound"
    compileSdk = 37

    defaultConfig {
        applicationId = "xtr.compound"
        minSdk = 31
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"

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
        aidl = true
        buildConfig = true
    }
}

dependencies {
    // ViewRootImpl background blur is hidden in the app process. Keep exemptions scoped.
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.libsu.core)
    implementation(libs.libsu.service)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    compileOnly(project(":app:hidden-api"))
}