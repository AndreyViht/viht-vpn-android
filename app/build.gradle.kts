import java.security.MessageDigest

plugins {
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val pingwinStoreFile = providers.gradleProperty("PINGWIN_STORE_FILE")
val pingwinStorePassword = providers.gradleProperty("PINGWIN_STORE_PASSWORD")
val pingwinKeyAlias = providers.gradleProperty("PINGWIN_KEY_ALIAS")
val pingwinKeyPassword = providers.gradleProperty("PINGWIN_KEY_PASSWORD")

val yandexNativeLogin = providers.gradleProperty("vihtYandexNativeLogin").map { it.toBooleanStrict() }.orElse(true)

val vkIdClientId = providers.gradleProperty("vihtVkClientId").orElse("54900535").get()
require(vkIdClientId.matches(Regex("[1-9][0-9]{5,11}"))) { "Invalid VK ID application identifier" }
val vkIdSdkKey = providers.environmentVariable("VIHT_VK_ANDROID_SDK_KEY").orElse("").get()

android {
    namespace = "com.pingwin.vpn"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        manifestPlaceholders["YANDEX_CLIENT_ID"] = "8bae435b834b473c84e424088436d23e"
        buildConfigField("boolean", "YANDEX_NATIVE_LOGIN", yandexNativeLogin.get().toString())
        manifestPlaceholders["VKIDClientID"] = vkIdClientId
        manifestPlaceholders["VKIDClientSecret"] = vkIdSdkKey.ifBlank { "server-pkce" }
        manifestPlaceholders["VKIDRedirectHost"] = "vk.ru"
        manifestPlaceholders["VKIDRedirectScheme"] = "vk$vkIdClientId"
        buildConfigField("boolean", "VK_USE_INSTALLED_APP", vkIdSdkKey.isNotBlank().toString())
        applicationId = "ru.anviht.vpn"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("ciVersionCode").orNull?.toInt() ?: 20014
        versionName = "2.0.0.14"
        manifestPlaceholders["appLabel"] = "@string/app_name"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }
    }

    packaging {
        jniLibs {
            // Compress universal libraries; Android extracts the matching ABI at installation.
            useLegacyPackaging = true
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file(System.getenv("VIHT_TEST_KEYSTORE") ?: (System.getProperty("user.home") + "/.android/debug.keystore"))
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (pingwinStoreFile.isPresent && pingwinStorePassword.isPresent && pingwinKeyAlias.isPresent && pingwinKeyPassword.isPresent) {
            create("release") {
                storeFile = file(pingwinStoreFile.get())
                storePassword = pingwinStorePassword.get()
                keyAlias = pingwinKeyAlias.get()
                keyPassword = pingwinKeyPassword.get()
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = false
            applicationIdSuffix = ".test"
            versionNameSuffix = ""
            manifestPlaceholders["appLabel"] = "Viht"
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            optimization {
                enable = false
            }
        }
    }
    splits {
        abi {
            isEnable = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("com.yandex.android:authsdk:3.1.3")
    implementation("com.vk.id:vkid:2.7.3")
    implementation("androidx.fragment:fragment:1.9.1")
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.joaomgcd:taskerpluginlibrary:0.4.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("org.json:json:20240303")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
	implementation(files("libs/libbox.aar"))
    implementation(files("libs/hev-socks5-tunnel.aar"))
}

