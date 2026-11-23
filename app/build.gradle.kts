plugins {
    id("com.android.application")
}

android {
    namespace = "app.tgnote.timegrapher"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.tgnote.timegrapher"   // 첫 업로드 후에는 바꿀 수 없음
        minSdk = 24
        targetSdk = 36
        versionCode = 1                            // 업로드할 때마다 1씩 올리기
        versionName = "1.0.0"
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

dependencies {
    implementation("com.android.billingclient:billing:8.0.0")
}
