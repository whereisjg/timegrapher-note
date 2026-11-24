plugins {
    id("com.android.application")
}

// Closed-test builds unlock Pro for every tester (gradle.properties: proFreeTesting=true). Anything else keeps Pro locked.
// BEFORE A PRODUCTION RELEASE set it to false (or delete the line); the version name shows "-test" while it is on.
val proFree = (project.findProperty("proFreeTesting") as String?) == "true"

android {
    namespace = "app.tgnote.timegrapher"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.tgnote.timegrapher"   // 첫 업로드 후에는 바꿀 수 없음
        minSdk = 24
        targetSdk = 36
        versionCode = 1                            // 업로드할 때마다 1씩 올리기
        versionName = if (proFree) "1.0.0-test" else "1.0.0"
        resValue("bool", "pro_free", proFree.toString())
    }

    buildFeatures {
        resValues = true
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
