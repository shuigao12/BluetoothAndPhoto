plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.rokid.cxrmsamples"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.rokid.cxrmsamples"
        minSdk = 31
        targetSdk = 36
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    // ---------- Android 基础与 Jetpack ----------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat) // 兼容旧组件，可选保留
    implementation(libs.androidx.constraintlayout) // 仍可用于非 Compose 布局
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // ---------- Compose UI ----------
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // ---------- 测试 ----------
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // ---------- 网络请求 ----------
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okio:okio:2.8.0")

    // ---------- 表格展示 ----------
    implementation("com.github.huangyanbin:SmartTable:2.2.0")

    // ---------- Rokid SDK ----------
    implementation("com.rokid.cxr:client-m:1.0.4") {
        exclude(group = "com.rokid.cxr", module = "client-m-sources")
    }

    // ---------- PyTorch Lite ----------
    implementation("org.pytorch:pytorch_android_lite:2.1.0")
    implementation("org.pytorch:pytorch_android_torchvision_lite:2.1.0")
    androidTestImplementation("org.pytorch:pytorch_android_lite:2.1.0")

    // ---------- Excel 处理 ----------
    implementation("net.sourceforge.jexcelapi:jxl:2.6.12")
}
