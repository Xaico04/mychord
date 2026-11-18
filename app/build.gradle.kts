plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

// numero da versao = numero da execucao no GitHub Actions (sobe sozinho a cada build)
val verCode = (project.findProperty("verCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.hichord.app"
    compileSdk = 34
    defaultConfig { applicationId = "com.hichord.app"; minSdk = 24; targetSdk = 34; versionCode = verCode; versionName = "1.$verCode" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }

    // chave fixa (vem dos Secrets do GitHub): sem ela o Android nao aceita atualizar por cima
    val ksPath = System.getenv("KEYSTORE_FILE")
    if (ksPath != null) {
        signingConfigs {
            create("fixed") {
                storeFile = file(ksPath)
                storeType = "pkcs12"
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "hichord"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
        buildTypes { getByName("debug") { signingConfig = signingConfigs.getByName("fixed") } }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
