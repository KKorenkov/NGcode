plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ngcode.voxphone"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ngcode.voxphone"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("com.voximplant:android-sdk-bom:3.3.0"))
    implementation("com.voximplant:android-sdk-core")
    implementation("com.voximplant:android-sdk-calls")
}
