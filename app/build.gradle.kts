plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.memorypin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.memorypin"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = project.findProperty("MYAPP_RELEASE_STORE_FILE") as String?
            val storePassword = project.findProperty("MYAPP_RELEASE_STORE_PASSWORD") as String?
            val keyAlias = project.findProperty("MYAPP_RELEASE_KEY_ALIAS") as String?
            val keyPassword = project.findProperty("MYAPP_RELEASE_KEY_PASSWORD") as String?

            if (storeFilePath != null &&
                storePassword != null &&
                keyAlias != null &&
                keyPassword != null
            ) {
                storeFile = rootProject.file(storeFilePath)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 키스토어가 설정된 경우에만 release 서명을 적용합니다.
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        viewBinding = true
    }
}

android.applicationVariants.all {
    outputs.all {
        val output = this
        val variantName = name
        if (output is com.android.build.gradle.internal.api.BaseVariantOutputImpl) {
            output.outputFileName = "MemoryPin-${variantName}.apk"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-ads:23.6.0")
}
