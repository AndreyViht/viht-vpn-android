plugins { alias(libs.plugins.android.application) }

android {
    namespace = "ru.anviht.admin"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "ru.anviht.admin.test"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("ciVersionCode").orNull?.toInt() ?: 1
        versionName = "2.0.0.5.test"
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file(System.getenv("VIHT_TEST_KEYSTORE") ?: "${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes { debug { isDebuggable = false } }
}
dependencies { implementation("androidx.webkit:webkit:1.12.1") }
